package com.ae2conveniencefeatures.wireless;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S09PacketHeldItemChange;
import net.minecraft.util.ChatComponentTranslation;
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
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.container.implementations.ContainerCraftAmount;
import appeng.core.sync.GuiBridge;
import appeng.util.Platform;
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
        if (player.dimension != request.dimension || request.items.size() > InventoryQueryPacket.MAX_ITEMS
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
        IItemList<IAEItemStack> stored = monitor == null ? null : monitor.getStorageList();
        ICraftingGrid crafting = monitor == null ? null
            : access.object.getGrid()
                .getCache(ICraftingGrid.class);
        boolean canCraft = crafting != null
            && TerminalAccess.permits(player, access.object.getGrid(), SecurityPermissions.CRAFT);
        if (monitor != null) reply.gridId = gridId(access.object.getGrid());
        observe(player, monitor, reply.gridId);
        Observer observer = OBSERVERS.get(player);
        reply.revision = observer == null ? 0 : observer.revision.get();

        for (ItemStack item : request.items) {
            IAEItemStack key = AEItemStack.create(item);
            IAEItemStack existing = stored == null ? null : stored.findPrecise(key);
            long count = existing == null ? 0 : Math.max(0, existing.getStackSize());
            if (count > 0 && request.extractableOnly) {
                // The world's middle-click branch needs extractable stock, including storage
                // filters; use only a one-item simulation after the cached lookup, never a scan.
                IAEItemStack available = monitor.extractItems(
                    key.copy()
                        .setStackSize(1),
                    Actionable.SIMULATE,
                    new PlayerSource(player, access.object));
                if (available == null || available.getStackSize() < 1) count = 0;
            }
            reply.items.add(item);
            reply.counts.add(count);
            reply.craftable.add(
                canCraft && !crafting.getCraftingFor(key, null, -1, player.worldObj)
                    .isEmpty());
        }
        if (observer != null && observer.revision.get() == reply.revision) observer.dirty = false;
        ModNetwork.CHANNEL.sendTo(reply, player);
    }

    public static void act(EntityPlayerMP player, WirelessActionPacket request) {
        boolean craftOnly = request.action == WirelessActionPacket.CRAFT_ONLY;
        if ((craftOnly ? !Config.neiMiddleCraft : !Config.neiActions) || player.dimension != request.dimension
            || throttle(ACTIONS, player, 100_000_000L)) return;
        TerminalAccess access = TerminalAccess.resolveServer(player);
        if (access == null || !access.accessible) {
            tell(player, "wireless.unavailable");
            ModNetwork.CHANNEL.sendTo(new InventoryInvalidationPacket(0, 0), player);
            return;
        }
        IMEMonitor<IAEItemStack> monitor = access.object.getItemInventory();
        if (monitor == null) return;
        ItemStack item = request.item;
        if (item == null || item.getItem() == null) return;
        IAEItemStack key = AEItemStack.create(item);
        key.setStackSize(1);
        // An explicit NEI middle-click always crafts, including stocked items. This branch
        // exits before even simulating extraction; the server rechecks the actual pattern and CRAFT permission.
        if (craftOnly) {
            openCrafting(player, access, key, true);
            return;
        }
        PlayerSource source = new PlayerSource(player, access.object);
        IAEItemStack available = monitor.extractItems(key.copy(), Actionable.SIMULATE, source);

        if (available == null || available.getStackSize() < 1) {
            openCrafting(player, access, key, true);
            return;
        }

        int wanted = request.action == WirelessActionPacket.TAKE_ONE ? 1 : item.getMaxStackSize();
        int amount = Math.min(wanted, capacity(player, item));
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
        player.inventory.addItemStackToInventory(delivered);
        if (delivered.stackSize > 0) {
            // An inventory hook may reject insertion despite the capacity check. Return leftovers;
            // if reinsertion is also rejected, drop only the actual extracted remainder, never a copy.
            IAEItemStack remainder = monitor.injectItems(AEItemStack.create(delivered), Actionable.MODULATE, source);
            if (remainder != null && remainder.getStackSize() > 0) {
                player.dropPlayerItemWithRandomChoice(remainder.getItemStack(), false);
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

    private static int capacity(EntityPlayerMP player, ItemStack item) {
        int max = Math.min(item.getMaxStackSize(), player.inventory.getInventoryStackLimit());
        int room = 0;
        for (int i = 0; i < Math.min(36, player.inventory.mainInventory.length); i++) {
            ItemStack slot = player.inventory.mainInventory[i];
            if (slot == null) room += max;
            else if (slot.isItemEqual(item) && ItemStack.areItemStackTagsEqual(slot, item)) {
                room += Math.max(0, Math.min(max, slot.getMaxStackSize()) - slot.stackSize);
            }
            if (room >= max) return max;
        }
        return room;
    }

    public static void pick(EntityPlayerMP player, WorldPickRequestPacket request) {
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
            || request.selectedSlot < 0
            || request.selectedSlot > 8
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

    private static void openCrafting(EntityPlayerMP player, TerminalAccess access, IAEItemStack item,
        boolean feedback) {
        ICraftingGrid crafting = access.object.getGrid()
            .getCache(ICraftingGrid.class);
        if (crafting == null || crafting.getCraftingFor(item, null, -1, player.worldObj)
            .isEmpty()) {
            if (feedback) tell(player, "wireless.no_pattern");
            return;
        }
        if (!TerminalAccess.permits(player, access.object.getGrid(), SecurityPermissions.CRAFT)) {
            if (feedback) tell(player, "wireless.no_craft_permission");
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

    private static void observe(EntityPlayerMP player, IMEMonitor<IAEItemStack> monitor, long gridId) {
        Observer old = OBSERVERS.get(player);
        if (old != null && old.monitor != monitor) {
            old.monitor.removeListener(old);
            OBSERVERS.remove(player);
            old = null;
        }
        if (monitor == null) return;
        if (old == null) {
            old = new Observer(player, monitor, gridId);
            OBSERVERS.put(player, old);
            monitor.addListener(old, monitor);
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
                observer.monitor.removeListener(observer);
                iterator.remove();
            } else if (observer.dirty && now - observer.lastSent > 250_000_000L) {
                observer.dirty = false;
                observer.lastSent = now;
                ModNetwork.CHANNEL
                    .sendTo(new InventoryInvalidationPacket(observer.gridId, observer.revision.get()), player);
            }
        }
    }

    private static final class Observer implements IMEMonitorHandlerReceiver<IAEItemStack> {

        final WeakReference<EntityPlayerMP> player;
        final IMEMonitor<IAEItemStack> monitor;
        final long gridId;
        long lastQuery;
        long lastSent;
        volatile boolean dirty;
        final AtomicLong revision = new AtomicLong(CHANGES.incrementAndGet());

        Observer(EntityPlayerMP player, IMEMonitor<IAEItemStack> monitor, long gridId) {
            this.player = new WeakReference<EntityPlayerMP>(player);
            this.monitor = monitor;
            this.gridId = gridId;
        }

        @Override
        public boolean isValid(Object token) {
            return token == monitor && player.get() != null;
        }

        @Override
        public void postChange(IBaseMonitor<IAEItemStack> monitor, Iterable<IAEItemStack> changes,
            BaseActionSource source) {
            revision.set(CHANGES.incrementAndGet());
            dirty = true;
        }

        @Override
        public void onListUpdate() {
            revision.set(CHANGES.incrementAndGet());
            dirty = true;
        }
    }
}
