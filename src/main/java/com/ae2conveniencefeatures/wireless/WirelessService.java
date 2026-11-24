package com.ae2conveniencefeatures.wireless;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S09PacketHeldItemChange;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.ae2conveniencefeatures.Config;
import com.ae2conveniencefeatures.network.ModNetwork;
import com.ae2conveniencefeatures.network.wireless.InventoryInvalidationPacket;
import com.ae2conveniencefeatures.network.wireless.InventoryQueryPacket;
import com.ae2conveniencefeatures.network.wireless.InventorySnapshotPacket;
import com.ae2conveniencefeatures.network.wireless.WirelessActionPacket;
import com.ae2conveniencefeatures.network.wireless.WorldPickRequestPacket;
import com.ae2conveniencefeatures.network.wireless.WorldPickResultPacket;

import appeng.api.config.Actionable;
import appeng.api.config.SecurityPermissions;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.PlayerSource;
import appeng.api.networking.storage.IBaseMonitor;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.container.implementations.ContainerCraftAmount;
import appeng.core.sync.GuiBridge;
import appeng.util.Platform;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;

/** All methods and maps in this class are used exclusively on the server tick thread. */
public final class WirelessService {

    private static final Map<IGrid, Long> GRID_IDS = new WeakHashMap<IGrid, Long>();
    private static final Map<EntityPlayerMP, Long> QUERIES = new WeakHashMap<EntityPlayerMP, Long>();
    private static final Map<EntityPlayerMP, Long> ACTIONS = new WeakHashMap<EntityPlayerMP, Long>();
    private static final Map<EntityPlayerMP, Long> PICK_IDS = new WeakHashMap<EntityPlayerMP, Long>();
    private static final Map<EntityPlayerMP, Observer> OBSERVERS = new WeakHashMap<EntityPlayerMP, Observer>();
    private static long nextGridId;
    private static final AtomicLong CHANGES = new AtomicLong();

    private WirelessService() {}

    private static long gridId(IGrid grid) {
        Long id = GRID_IDS.get(grid);
        if (id == null) {
            id = ++nextGridId;
            GRID_IDS.put(grid, id);
        }
        return id;
    }

    private static boolean throttle(Map<EntityPlayerMP, Long> map, EntityPlayerMP player, long nanos) {
        long now = System.nanoTime();
        Long previous = map.get(player);
        if (previous != null && now - previous < nanos) return true;
        map.put(player, now);
        return false;
    }

    public static void query(EntityPlayerMP player, InventoryQueryPacket request) {
        if (player.dimension != request.dimension || request.targets.size() > InventoryQueryPacket.MAX_ITEMS
            || throttle(QUERIES, player, 40_000_000L)) return;

        TerminalAccess access = TerminalAccess.resolveServer(player);
        InventorySnapshotPacket reply = new InventorySnapshotPacket();
        reply.requestId = request.requestId;
        reply.dimension = player.dimension;
        reply.terminalSlot = access == null ? -1 : access.slot;
        reply.validTerminal = access != null;
        reply.accessible = access != null && access.accessible;
        // Range/security failure exposes no inventory and cannot enter the crafting branch.
        IMEMonitor<IAEItemStack> monitor = reply.accessible ? access.object.getItemInventory() : null;
        IMEMonitor<IAEFluidStack> fluidMonitor = reply.accessible ? access.object.getFluidInventory() : null;
        IItemList<IAEItemStack> stored = monitor == null ? null : monitor.getStorageList();
        IItemList<IAEFluidStack> fluids = fluidMonitor == null ? null : fluidMonitor.getStorageList();
        ICraftingGrid crafting = !reply.accessible ? null
            : access.object.getGrid()
                .getCache(ICraftingGrid.class);
        boolean canCraft = crafting != null
            && TerminalAccess.permits(player, access.object.getGrid(), SecurityPermissions.CRAFT);
        Map<IAEStack<?>, ?> fluidPatterns = canCraft && request.targets.stream()
            .anyMatch(WirelessTarget::isFluid) ? crafting.getCraftingMultiPatterns() : Collections.emptyMap();
        if (reply.accessible) reply.gridId = gridId(access.object.getGrid());
        observe(player, monitor, fluidMonitor, reply.gridId);
        Observer observer = OBSERVERS.get(player);
        reply.revision = observer == null ? 0 : observer.revision.get();

        PlayerSource source = reply.accessible && request.extractableOnly ? new PlayerSource(player, access.object)
            : null;
        for (WirelessTarget target : request.targets) {
            IAEStack<?> key;
            long count;
            if (target.isFluid()) {
                IAEFluidStack fluid = AEFluidStack.create(target.fluid());
                key = fluid;
                count = count(fluidMonitor, fluids, fluid, source);
            } else {
                IAEItemStack item = AEItemStack.create(target.item());
                key = item;
                count = count(monitor, stored, item, source);
            }
            if (key == null) continue;
            reply.targets.add(target);
            reply.counts.add(count);
            reply.craftable.add(canCraft && hasPattern(crafting, fluidPatterns, key, player.worldObj));
        }
        if (observer != null && observer.revision.get() == reply.revision) observer.dirty = false;
        ModNetwork.CHANNEL.sendTo(reply, player);
    }

    private static <T extends IAEStack<T>> long count(IMEMonitor<T> monitor, IItemList<T> stored, T key,
        PlayerSource extractableOnly) {
        if (stored == null || key == null) return 0;
        T existing = stored.findPrecise(key);
        if (existing == null || existing.getStackSize() <= 0) return 0;
        if (extractableOnly != null) {
            T available = monitor.extractItems(
                key.copy()
                    .setStackSize(1),
                Actionable.SIMULATE,
                extractableOnly);
            if (available == null || available.getStackSize() < 1) return 0;
        }
        return existing.getStackSize();
    }

    public static void act(EntityPlayerMP player, WirelessActionPacket request) {
        if (request.action < WirelessActionPacket.TAKE_ONE || request.action > WirelessActionPacket.FILL_INVENTORY)
            return;
        boolean craftOnly = request.action == WirelessActionPacket.CRAFT_ONLY;
        if ((craftOnly ? !Config.neiMiddleCraft : !Config.neiActions) || player.dimension != request.dimension
            || throttle(ACTIONS, player, 100_000_000L)) return;
        TerminalAccess access = TerminalAccess.resolveServer(player);
        if (access == null || !access.accessible) {
            tell(player, "wireless.unavailable");
            ModNetwork.CHANNEL.sendTo(new InventoryInvalidationPacket(0, 0), player);
            return;
        }
        WirelessTarget target = request.target;
        if (target == null) return;
        if (target.isFluid()) {
            // A display fluid cannot be inserted directly into the player's backpack.
            openCrafting(player, access, AEFluidStack.create(target.fluid()), true);
            return;
        }
        ItemStack item = target.item();
        IAEItemStack key = AEItemStack.create(item);
        if (key == null) return;
        // An explicit NEI middle-click always crafts, including stocked items. This branch
        // exits before even simulating extraction; the server rechecks the actual pattern and CRAFT permission.
        if (craftOnly) {
            openCrafting(player, access, key, true);
            return;
        }
        IMEMonitor<IAEItemStack> monitor = access.object.getItemInventory();
        if (monitor == null) return;
        PlayerSource source = new PlayerSource(player, access.object);
        IAEItemStack available = monitor.extractItems(key.copy(), Actionable.SIMULATE, source);

        if (available == null || available.getStackSize() < 1) {
            openCrafting(player, access, key, true);
            return;
        }

        boolean fill = request.action == WirelessActionPacket.FILL_INVENTORY;
        int room = BackpackInventory.capacity(player.inventory, item);
        int wanted = fill ? room : request.action == WirelessActionPacket.TAKE_ONE ? 1 : item.getMaxStackSize();
        int amount = Math.min(wanted, room);
        if (amount <= 0) {
            tell(player, "wireless.inventory_full");
            return;
        }
        key.setStackSize(amount);
        IAEItemStack extracted = Platform.poweredExtraction(access.object, monitor, key, source);
        if (extracted == null || extracted.getStackSize() <= 0) {
            tell(player, "wireless.extract_failed");
            return;
        }

        ItemStack delivered = extracted.getItemStack();
        // A fill can exceed one stack. Split it legally and recheck slots after storage hooks ran.
        // Never open crafting after this point, even if stock or power only covers part of the request.
        if (fill) BackpackInventory.insert(player.inventory, delivered);
        else player.inventory.addItemStackToInventory(delivered);
        if (delivered.stackSize > 0) {
            // An inventory hook may reject insertion despite the capacity check. Return leftovers;
            // if reinsertion is also rejected, drop only the actual extracted remainder, never a copy.
            IAEItemStack remainder = monitor.injectItems(AEItemStack.create(delivered), Actionable.MODULATE, source);
            if (remainder != null && remainder.getStackSize() > 0) {
                ItemStack unreturned = remainder.getItemStack();
                while (unreturned.stackSize > 0) {
                    player.dropPlayerItemWithRandomChoice(
                        unreturned.splitStack(Math.min(unreturned.stackSize, unreturned.getMaxStackSize())),
                        false);
                }
            }
        }
        player.inventory.markDirty();
        player.inventoryContainer.detectAndSendChanges();
        player.openContainer.detectAndSendChanges();
        Observer observer = OBSERVERS.get(player);
        ModNetwork.CHANNEL.sendTo(
            new InventoryInvalidationPacket(
                gridId(access.object.getGrid()),
                observer == null ? CHANGES.incrementAndGet() : observer.revision.get()),
            player);
    }

    public static void pick(EntityPlayerMP player, WorldPickRequestPacket request) {
        if (!WorldPickRequestPacket.isVanillaSlot(request.selectedSlot)) return;
        Long previous = PICK_IDS.get(player);
        if (previous != null && request.requestId <= previous) {
            pickResult(player, request, false);
            return;
        }
        PICK_IDS.put(player, request.requestId);
        if (!Config.worldPickBlock) {
            pickResult(player, request, true);
            return;
        }
        if (player.dimension != request.dimension || player.capabilities.isCreativeMode
            || player.openContainer != player.inventoryContainer
            || player.inventory.currentItem != request.selectedSlot
            || throttle(ACTIONS, player, 100_000_000L)) {
            pickResult(player, request, false);
            return;
        }
        ItemStack item = worldTarget(player, request);
        if (item == null) {
            pickResult(player, request, false);
            return;
        }
        TerminalAccess access = TerminalAccess.resolveServer(player);
        if (access == null || !access.accessible) {
            pickResult(player, request, true);
            return;
        }
        // Prefer items the player already owns, like vanilla pick-block. This also makes a
        // previously completed competing picker harmless instead of extracting a second stack.
        int existing = HandInventory.selectExisting(player.inventory, request.selectedSlot, item);
        if (existing >= 0) {
            selectHand(player, existing);
            pickResult(player, request, false);
            return;
        }
        IMEMonitor<IAEItemStack> monitor = access.object.getItemInventory();
        if (monitor == null) {
            pickResult(player, request, true);
            return;
        }
        IAEItemStack key = AEItemStack.create(item);
        key.setStackSize(1);
        PlayerSource source = new PlayerSource(player, access.object);
        IAEItemStack available = monitor.extractItems(key.copy(), Actionable.SIMULATE, source);
        if (available == null || available.getStackSize() < 1) {
            ICraftingGrid crafting = access.object.getGrid()
                .getCache(ICraftingGrid.class);
            if (crafting != null && !crafting.getCraftingFor(key, null, -1, player.worldObj)
                .isEmpty()) {
                openCrafting(player, access, key, true);
                pickResult(player, request, false);
            } else {
                tell(player, "wireless.no_pattern");
                pickResult(player, request, true);
            }
            return;
        }
        if (!HandInventory.hasRoom(player.inventory, request.selectedSlot)) {
            tell(player, "wireless.inventory_full");
            pickResult(player, request, false);
            return;
        }
        key.setStackSize(item.getMaxStackSize());
        IAEItemStack extracted = Platform.poweredExtraction(access.object, monitor, key, source);
        if (extracted == null || extracted.getStackSize() <= 0) {
            tell(player, "wireless.extract_failed");
            pickResult(player, request, false);
            return;
        }
        ItemStack delivered = extracted.getItemStack();
        // Recompute placement after extraction: a storage/inventory hook may have changed slots.
        int slot = HandInventory.place(player.inventory, request.selectedSlot, delivered);
        if (slot < 0) {
            IAEItemStack remainder = monitor.injectItems(extracted, Actionable.MODULATE, source);
            if (remainder != null && remainder.getStackSize() > 0) {
                player.dropPlayerItemWithRandomChoice(remainder.getItemStack(), false);
            }
            tell(player, "wireless.inventory_full");
        } else selectHand(player, slot);
        pickResult(player, request, false);
    }

    private static void selectHand(EntityPlayerMP player, int slot) {
        player.inventory.currentItem = slot;
        player.inventory.markDirty();
        player.inventoryContainer.detectAndSendChanges();
        player.playerNetServerHandler.sendPacket(new S09PacketHeldItemChange(slot));
    }

    private static void pickResult(EntityPlayerMP player, WorldPickRequestPacket request, boolean vanillaFallback) {
        ModNetwork.CHANNEL
            .sendTo(new WorldPickResultPacket(request.requestId, request.dimension, vanillaFallback), player);
    }

    private static ItemStack worldTarget(EntityPlayerMP player, WorldPickRequestPacket request) {
        if (request.y < 0 || request.y > 255
            || request.side < 0
            || request.side > 5
            || player.getDistanceSq(request.x + 0.5, request.y + 0.5, request.z + 0.5) > 64
            || !player.worldObj.blockExists(request.x, request.y, request.z)
            || player.worldObj.isAirBlock(request.x, request.y, request.z)
            || request.item == null) return null;
        // Forge 1.7.10 getPickBlock delegates to client-only Block.getItem on a dedicated
        // server. Only the client resolves it; this target is advisory, like an NEI selection.
        // The server rechecks access, actual stock and patterns. A forged target grants no
        // ability beyond the already permitted NEI selection/extraction path.
        return request.item.copy();
    }

    private static boolean hasPattern(ICraftingGrid crafting, Map<IAEStack<?>, ?> fluidPatterns, IAEStack<?> key,
        World world) {
        // GTNH AE2's getCraftingFor(IAEStack) only matches items. Its multi-pattern index
        // contains native fluid outputs; keep the original item overload for legacy item conversion.
        return key instanceof IAEItemStack item ? !crafting.getCraftingFor(item, null, -1, world)
            .isEmpty() : fluidPatterns.containsKey(key);
    }

    private static void openCrafting(EntityPlayerMP player, TerminalAccess access, IAEStack<?> item, boolean feedback) {
        ICraftingGrid crafting = access.object.getGrid()
            .getCache(ICraftingGrid.class);
        if (!TerminalAccess.permits(player, access.object.getGrid(), SecurityPermissions.CRAFT)) {
            if (feedback) tell(player, "wireless.no_craft_permission");
            return;
        }
        if (item == null || crafting == null
            || !hasPattern(
                crafting,
                item.isFluid() ? crafting.getCraftingMultiPatterns() : Collections.emptyMap(),
                item,
                player.worldObj)) {
            if (feedback) tell(player, "wireless.no_pattern");
            return;
        }
        // AE2 owns quantity entry, confirmation, job submission, and its own permission checks.
        Container previous = player.openContainer;
        Platform.openGUI(player, null, ForgeDirection.UNKNOWN, GuiBridge.GUI_CRAFTING_AMOUNT, access.slot);
        if (player.openContainer != previous && player.openContainer instanceof ContainerCraftAmount) {
            ContainerCraftAmount amount = (ContainerCraftAmount) player.openContainer;
            // PacketCraftRequest carries this target into ContainerCraftConfirm. Without it,
            // AE2 submits the job and then throws while returning, disconnecting the player.
            amount.setPrimaryGui(new QuickCraftingReturn(access.stack));
            amount.setItemToCraft(
                item.copy()
                    .setStackSize(1));
            amount.setInitialCraftAmount(1);
            amount.detectAndSendChanges();
        } else if (feedback) tell(player, "wireless.gui_failed");
    }

    private static void tell(EntityPlayerMP player, String key) {
        player.addChatMessage(new ChatComponentTranslation("ae2cf." + key));
    }

    private static void observe(EntityPlayerMP player, IMEMonitor<IAEItemStack> items, IMEMonitor<IAEFluidStack> fluids,
        long gridId) {
        Observer old = OBSERVERS.get(player);
        if (old != null && (old.gridId != gridId || old.items.monitor != items || old.fluids.monitor != fluids)) {
            old.removeListeners();
            OBSERVERS.remove(player);
            old = null;
        }
        if (items == null && fluids == null) return;
        if (old == null) {
            old = new Observer(player, items, fluids, gridId);
            OBSERVERS.put(player, old);
        }
        old.lastQuery = System.nanoTime();
    }

    public static void flushChanges() {
        long now = System.nanoTime();
        Iterator<Map.Entry<EntityPlayerMP, Observer>> iterator = OBSERVERS.entrySet()
            .iterator();
        while (iterator.hasNext()) {
            Map.Entry<EntityPlayerMP, Observer> row = iterator.next();
            Observer observer = row.getValue();
            EntityPlayerMP player = row.getKey();
            if (player.isDead || now - observer.lastQuery > 3_000_000_000L
                || !player.worldObj.playerEntities.contains(player)) {
                observer.removeListeners();
                iterator.remove();
            } else if (observer.dirty && now - observer.lastSent > 250_000_000L) {
                observer.dirty = false;
                observer.lastSent = now;
                ModNetwork.CHANNEL
                    .sendTo(new InventoryInvalidationPacket(observer.gridId, observer.revision.get()), player);
            }
        }
    }

    private static final class Observer {

        final WeakReference<EntityPlayerMP> player;
        final Listener<IAEItemStack> items;
        final Listener<IAEFluidStack> fluids;
        final long gridId;
        long lastQuery;
        long lastSent;
        volatile boolean dirty;
        final AtomicLong revision = new AtomicLong(CHANGES.incrementAndGet());

        Observer(EntityPlayerMP player, IMEMonitor<IAEItemStack> items, IMEMonitor<IAEFluidStack> fluids, long gridId) {
            this.player = new WeakReference<EntityPlayerMP>(player);
            this.gridId = gridId;
            this.items = new Listener<>(items);
            this.fluids = new Listener<>(fluids);
        }

        void removeListeners() {
            items.remove();
            fluids.remove();
        }

        private final class Listener<T extends IAEStack<T>> implements IMEMonitorHandlerReceiver<T> {

            final IMEMonitor<T> monitor;

            Listener(IMEMonitor<T> monitor) {
                this.monitor = monitor;
                if (monitor != null) monitor.addListener(this, monitor);
            }

            void remove() {
                if (monitor != null) monitor.removeListener(this);
            }

            @Override
            public boolean isValid(Object token) {
                return token == monitor && player.get() != null;
            }

            @Override
            public void postChange(IBaseMonitor<T> monitor, Iterable<T> changes, BaseActionSource source) {
                onListUpdate();
            }

            @Override
            public void onListUpdate() {
                revision.set(CHANGES.incrementAndGet());
                dirty = true;
            }
        }
    }
}
