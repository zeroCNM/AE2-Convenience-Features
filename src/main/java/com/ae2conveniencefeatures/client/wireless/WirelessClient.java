package com.ae2conveniencefeatures.client.wireless;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.opengl.GL11;

import com.ae2conveniencefeatures.Config;
import com.ae2conveniencefeatures.mixin.nei.HistoryGridAccessor;
import com.ae2conveniencefeatures.mixin.nei.RecipeContainerAccessor;
import com.ae2conveniencefeatures.network.ModNetwork;
import com.ae2conveniencefeatures.network.wireless.InventoryQueryPacket;
import com.ae2conveniencefeatures.network.wireless.InventorySnapshotPacket;
import com.ae2conveniencefeatures.network.wireless.WirelessActionPacket;
import com.ae2conveniencefeatures.network.wireless.WorldPickRequestPacket;
import com.ae2conveniencefeatures.network.wireless.WorldPickResultPacket;
import com.ae2conveniencefeatures.wireless.InventoryViewCache;
import com.ae2conveniencefeatures.wireless.StackKey;
import com.ae2conveniencefeatures.wireless.StackKeyCache;
import com.ae2conveniencefeatures.wireless.TerminalAccess;

import codechicken.lib.gui.GuiDraw;
import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.ItemPanels;
import codechicken.nei.ItemsGrid;
import codechicken.nei.ItemsGrid.ItemsGridSlot;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.Widget;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.guihook.IContainerObjectHandler;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.scroll.ScrollContainer;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public final class WirelessClient {

    private static final InventoryViewCache<StackKey> CACHE = new InventoryViewCache<StackKey>();
    private static final StackKeyCache KEYS = new StackKeyCache();
    private static boolean hasSnapshot;
    private static boolean validTerminal;
    private static boolean accessible;
    private static PendingPick pendingPick;
    private static long pickSequence;

    public static void register() {
        WirelessClient events = new WirelessClient();
        MinecraftForge.EVENT_BUS.register(events);
        FMLCommonHandler.instance()
            .bus()
            .register(events);
        // GuiContainerManager calls handlers in list order. Our narrowly scoped Shift clicks must
        // run before NEI's standard recipe/cheat clicks; all other input returns false.
        GuiContainerManager.inputHandlers.addFirst(new WirelessInputHandler());
    }

    private static boolean neiVisible() {
        return NEIClientConfig.isLoaded() && NEIClientConfig.isEnabled() && !NEIClientConfig.isHidden();
    }

    public static boolean supportedGrid(ItemsGrid<?, ?> grid) {
        return grid == ItemPanels.itemPanel.getGrid() || grid == historyGrid()
            || grid == ItemPanels.bookmarkPanel.getGrid();
    }

    private static ItemsGrid<?, ?> historyGrid() {
        return ((HistoryGridAccessor) ItemPanels.itemPanel.historyPanel).ae2cf$getGrid();
    }

    @SubscribeEvent
    public void opened(GuiOpenEvent event) {
        pendingPick = null;
        KEYS.clear();
        CACHE.context(null, Collections.<StackKey>emptySet());
        CACHE.invalidate(0);
        hasSnapshot = false;
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        boolean neiActive = (Config.inventoryOverlay || Config.neiMiddleCraft)
            && minecraft.currentScreen instanceof GuiContainer
            && neiVisible();
        if (minecraft.theWorld == null || !neiActive && pendingPick == null) {
            pendingPick = null;
            CACHE.context(null, Collections.<StackKey>emptySet());
            hasSnapshot = false;
            return;
        }
        TerminalAccess terminal = TerminalAccess.findClientItem(minecraft.thePlayer);
        if (minecraft.theWorld == null || terminal == null) {
            pendingPick = null;
            CACHE.context(null, Collections.<StackKey>emptySet());
            hasSnapshot = false;
            return;
        }
        LinkedHashMap<StackKey, ItemStack> wanted = new LinkedHashMap<StackKey, ItemStack>();
        if (neiActive && minecraft.currentScreen instanceof GuiContainer gui) {
            Point mouse = GuiDraw.getMousePosition();
            ItemStack hovered = neiCraftItem(gui, mouse.x, mouse.y);
            if (hovered != null) wanted.put(KEYS.key(hovered), hovered);
            collect(ItemPanels.itemPanel.getGrid(), wanted);
            if (NEIClientConfig.showHistoryPanelWidget()) collect(historyGrid(), wanted);
            collect(ItemPanels.bookmarkPanel.getGrid(), wanted);
            if (Config.inventoryOverlay && gui instanceof GuiRecipe) collectRecipes((GuiRecipe<?>) gui, wanted);
        }
        String identity = System.identityHashCode(minecraft.theWorld) + ":"
            + System.identityHashCode(minecraft.currentScreen)
            + ":"
            + terminal.clientIdentity();
        if (CACHE.context(identity, wanted.keySet())) hasSnapshot = false;
        long now = System.nanoTime();
        if (pendingPick != null && !pendingPick.isCurrent(minecraft, terminal, now)) pendingPick = null;
        if (wanted.isEmpty() || CACHE.pending(now)) return;
        ArrayList<ItemStack> batch = new ArrayList<ItemStack>();
        for (StackKey key : CACHE.refreshCandidates(now, InventoryQueryPacket.MAX_ITEMS)) {
            batch.add(wanted.get(key));
        }
        if (!batch.isEmpty()) {
            ModNetwork.CHANNEL.sendToServer(
                new InventoryQueryPacket(
                    CACHE.begin(now),
                    minecraft.thePlayer.dimension,
                    batch,
                    minecraft.currentScreen == null));
        }
    }

    private static void collect(ItemsGrid<?, ?> grid, Map<StackKey, ItemStack> wanted) {
        if (grid.getPerPage() == 0) return;
        for (ItemsGridSlot slot : grid.getMask()) {
            ItemStack item = slot.getItemStack();
            if (item != null && item.getItem() != null) wanted.put(KEYS.key(item), item);
        }
    }

    private static void collectRecipes(GuiRecipe<?> gui, Map<StackKey, ItemStack> wanted) {
        ScrollContainer body = ((RecipeContainerAccessor) gui).ae2cf$getRecipeContainer();
        if (body == null) return;
        // Query all current recipe-body stacks on tick, without requiring a hovered item.
        // Never traverse recipeCatalyst: its machine list must not gain stock overlays.
        for (Widget widget : body.getWidgets()) {
            if (widget instanceof RecipeInventorySource source) {
                source.ae2cf$collectItems(stack -> {
                    if (stack != null && stack.item != null && stack.item.getItem() != null) {
                        wanted.put(KEYS.key(stack.item), stack.item);
                    }
                });
            }
        }
    }

    public static void receive(InventorySnapshotPacket packet) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.thePlayer == null || packet.dimension != minecraft.thePlayer.dimension) return;
        Map<StackKey, InventoryViewCache.Stock> values = new HashMap<StackKey, InventoryViewCache.Stock>();
        long now = System.nanoTime();
        for (int i = 0; i < packet.items.size(); i++) {
            values.put(
                new StackKey(packet.items.get(i)),
                new InventoryViewCache.Stock(packet.counts.get(i), packet.craftable.get(i), now));
        }
        if (CACHE.accept(packet.requestId, packet.gridId, packet.revision, values)) {
            hasSnapshot = true;
            validTerminal = packet.validTerminal;
            accessible = packet.accessible;
        }
    }

    public static void invalidate(long gridId, long revision) {
        if (gridId == 0) CACHE.invalidate(0);
        else CACHE.inventoryChanged(gridId, revision);
    }

    /** Called after NEI's framebuffer cache. Absolutely no packets, grid queries, or terminal scans here. */
    public static void draw(ItemsGrid<?, ?> grid) {
        if (!Config.inventoryOverlay || !hasSnapshot
            || !validTerminal
            || !accessible
            || !supportedGrid(grid)
            || grid.getPerPage() == 0) return;
        long now = System.nanoTime();
        boolean bookmark = grid == ItemPanels.bookmarkPanel.getGrid();
        beginStockDraw();
        try {
            for (ItemsGridSlot slot : grid.getMask()) {
                drawStock(slot.getItemStack(), grid.getSlotRect(slot.slotIndex), bookmark, now);
            }
        } finally {
            endStockDraw();
        }
    }

    /** Only called by recipe-body widgets, inside NEI's translation and clipping scope. */
    public static void drawRecipeItem(PositionedStack stack) {
        if (!Config.inventoryOverlay || !hasSnapshot || !validTerminal || !accessible || stack == null) return;
        beginStockDraw();
        try {
            drawStock(stack.item, new Rectangle4i(stack.relx, stack.rely, 16, 16), true, System.nanoTime());
        } finally {
            endStockDraw();
        }
    }

    private static void beginStockDraw() {
        GL11.glPushAttrib(
            GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glScalef(0.5F, 0.5F, 1);
    }

    private static void endStockDraw() {
        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    private static void drawStock(ItemStack item, Rectangle4i rect, boolean leftAligned, long now) {
        if (item == null) return;
        InventoryViewCache.Stock stock = CACHE.peekForDisplay(KEYS.key(item), now);
        if (stock == null) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        int textX = leftAligned ? (rect.x + 1) * 2
            : (rect.x + rect.w - 1) * 2 - minecraft.fontRenderer.getStringWidth(stock.display);
        int textY = (rect.y + rect.h) * 2 - minecraft.fontRenderer.FONT_HEIGHT;
        if (stock.count > 0) {
            minecraft.fontRenderer.drawStringWithShadow(stock.display, textX, textY, leftAligned ? 0x7EFFB3 : 0xFFFFFF);
        }
        if (stock.craftable) {
            minecraft.fontRenderer.drawStringWithShadow("+", (rect.x + 1) * 2, (rect.y + 1) * 2, 0xFFFFFF);
        }
    }

    public static boolean click(GuiContainer gui, int mouseX, int mouseY, int button) {
        if (!Config.neiActions || button != 0 && button != 1 || !NEIClientUtils.shiftKey() || !neiVisible())
            return false;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (TerminalAccess.findClientItem(minecraft.thePlayer) == null || hasSnapshot && !validTerminal) return false;
        ItemStack item = neiItem(gui, mouseX, mouseY);
        if (item == null) return false; // Ordinary player inventory slots retain vanilla Shift-click behavior.
        ModNetwork.CHANNEL.sendToServer(
            new WirelessActionPacket(
                button == 1 ? WirelessActionPacket.TAKE_ONE : WirelessActionPacket.TAKE_STACK,
                minecraft.thePlayer.dimension,
                item));
        CACHE.refresh();
        return true;
    }

    private static ItemStack neiItem(GuiContainer gui, int mouseX, int mouseY) {
        ItemStack item = ItemPanels.itemPanel.getStackMouseOver(mouseX, mouseY);
        if (item == null && NEIClientConfig.showHistoryPanelWidget()) {
            item = ItemPanels.itemPanel.historyPanel.getStackMouseOver(mouseX, mouseY);
        }
        if (item == null) item = ItemPanels.bookmarkPanel.getStackMouseOver(mouseX, mouseY);
        if (item == null && gui instanceof GuiRecipe) item = GuiContainerManager.getStackMouseOver(gui);
        return item;
    }

    private static ItemStack neiCraftItem(GuiContainer gui, int mouseX, int mouseY) {
        ItemStack item = neiItem(gui, mouseX, mouseY);
        if (item != null) return item;
        // Include every NEI-registered item widget, including custom panels, without using
        // getStackMouseOver's ordinary player-inventory fallback outside recipe pages.
        for (IContainerObjectHandler handler : GuiContainerManager.objectHandlers) {
            item = handler.getStackUnderMouse(gui, mouseX, mouseY);
            if (item != null) return item;
        }
        return null;
    }

    /** Called at NEI dispatch HEAD, before both notification and consumable input handlers. */
    public static boolean craftClick(GuiContainer gui, int mouseX, int mouseY, int button) {
        if (button != 2 || !Config.neiMiddleCraft || !neiVisible()) return false;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.thePlayer == null || minecraft.theWorld == null
            || minecraft.currentScreen != gui
            || TerminalAccess.findClientItem(minecraft.thePlayer) == null
            || hasSnapshot && (!validTerminal || !accessible)) return false;
        ItemStack item = neiCraftItem(gui, mouseX, mouseY);
        if (item == null) return false; // Does not claim ordinary inventory or AE terminal slots.
        InventoryViewCache.Stock stock = CACHE.peek(KEYS.key(item), System.nanoTime());
        if (stock != null && !stock.craftable) return false;
        // A cold/stale cache is advisory, never authority: claim this one press and let the
        // server validate it once. Do not replay it to other crafting handlers after a timeout.
        ModNetwork.CHANNEL.sendToServer(
            new WirelessActionPacket(WirelessActionPacket.CRAFT_ONLY, minecraft.thePlayer.dimension, item));
        return true;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void middle(MouseEvent event) {
        if (event.isCanceled()) return;
        if (!Config.worldPickBlock || event.button != 2 || !event.buttonstate) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.currentScreen != null || minecraft.thePlayer == null
            || minecraft.theWorld == null
            || minecraft.thePlayer.capabilities.isCreativeMode) return;
        TerminalAccess terminal = TerminalAccess.findClientItem(minecraft.thePlayer);
        if (terminal == null) return; // No terminal: do not touch vanilla pick-block or other mods.
        ItemStack item = pickedItem(minecraft);
        if (item == null) return;
        // Own this physical press before it becomes vanilla's pick key or another MouseInputEvent.
        // This prevents AE2's PacketPickBlock and middleClickMouse mixins from making a second
        // extraction. Only this terminal-backed world-block input is consumed; no global key or
        // other mod setting is changed. Already-canceled input above remains owned by its handler.
        event.setCanceled(true);
        long now = System.nanoTime();
        if (pendingPick != null && pendingPick.isCurrent(minecraft, terminal, now)) return;
        pendingPick = new PendingPick(minecraft, terminal, item, ++pickSequence, now);
        ModNetwork.CHANNEL.sendToServer(pendingPick.request);
    }

    public static void receivePickResult(WorldPickResultPacket result) {
        PendingPick pending = pendingPick;
        if (pending == null || result.requestId != pending.request.requestId) return;
        pendingPick = null;
        if (!result.vanillaFallback) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        TerminalAccess terminal = TerminalAccess.findClientItem(minecraft.thePlayer);
        if (result.dimension != pending.request.dimension || !pending.isCurrent(minecraft, terminal, System.nanoTime()))
            return;
        // The server confirmed it did not extract or open crafting. Invoke Forge's vanilla
        // inventory selection only, without re-emitting the input to network pickers a second time.
        ForgeHooks.onPickBlock(pending.hit, minecraft.thePlayer, minecraft.theWorld);
    }

    private static final class PendingPick {

        final Object world;
        final String terminal;
        final StackKey item;
        final MovingObjectPosition hit;
        final WorldPickRequestPacket request = new WorldPickRequestPacket();
        final long expires;

        PendingPick(Minecraft minecraft, TerminalAccess terminal, ItemStack item, long id, long now) {
            world = minecraft.theWorld;
            this.terminal = terminal.clientIdentity();
            this.item = new StackKey(item);
            hit = minecraft.objectMouseOver;
            expires = now + 3_000_000_000L;
            request.requestId = id;
            request.dimension = minecraft.thePlayer.dimension;
            request.selectedSlot = minecraft.thePlayer.inventory.currentItem;
            request.item = item.copy();
            request.item.stackSize = 1;
            request.x = hit.blockX;
            request.y = hit.blockY;
            request.z = hit.blockZ;
            request.side = hit.sideHit;
        }

        boolean isCurrent(Minecraft minecraft, TerminalAccess currentTerminal, long now) {
            if (minecraft.thePlayer == null || minecraft.theWorld != world
                || minecraft.currentScreen != null
                || currentTerminal == null
                || now >= expires
                || minecraft.thePlayer.dimension != request.dimension
                || minecraft.thePlayer.inventory.currentItem != request.selectedSlot
                || !terminal.equals(currentTerminal.clientIdentity())) return false;
            MovingObjectPosition current = minecraft.objectMouseOver;
            ItemStack picked = pickedItem(minecraft);
            return current != null && picked != null
                && item.matches(picked)
                && current.blockX == request.x
                && current.blockY == request.y
                && current.blockZ == request.z;
        }
    }

    private static ItemStack pickedItem(Minecraft minecraft) {
        MovingObjectPosition hit = minecraft.objectMouseOver;
        if (minecraft.theWorld == null || hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK)
            return null;
        return minecraft.theWorld.getBlock(hit.blockX, hit.blockY, hit.blockZ)
            .getPickBlock(hit, minecraft.theWorld, hit.blockX, hit.blockY, hit.blockZ, minecraft.thePlayer);
    }
}
