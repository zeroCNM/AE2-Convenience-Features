package com.ae2conveniencefeatures.client.crafting;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.common.MinecraftForge;

import com.ae2conveniencefeatures.Config;
import com.ae2conveniencefeatures.crafting.RecentCraftHistory;
import com.ae2conveniencefeatures.network.crafting.CompletedCraftsPacket;
import com.ae2conveniencefeatures.wireless.StackKey;

import appeng.client.gui.implementations.GuiCraftAmount;
import appeng.client.gui.implementations.GuiCraftConfirm;
import appeng.client.gui.implementations.GuiCraftingCPU;
import appeng.client.gui.implementations.GuiCraftingStatus;
import appeng.client.gui.implementations.GuiMEMonitorable;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Client main thread only. No inventory quantities or extraction decisions originate here. */
public final class RecentTerminalClient {

    private static final RecentCraftHistory<StackKey, ItemStack> HISTORY = new RecentCraftHistory<>();
    private static final Map<Object, WeakReference<RecentRepo>> REPOS = new WeakHashMap<>();
    private static final Deque<Pending> PENDING = new ArrayDeque<>();
    private static Object world;
    private static long lastSequence;
    private static long gridId;
    private static int windowId = -1;
    private static boolean wasEnabled = true;

    public static void register() {
        RecentTerminalClient events = new RecentTerminalClient();
        MinecraftForge.EVENT_BUS.register(events);
        FMLCommonHandler.instance()
            .bus()
            .register(events);
    }

    public static void bindRepo(Object owner, RecentRepo repo) {
        resetWorld();
        if (owner instanceof GuiMEMonitorable) REPOS.put(owner, new WeakReference<RecentRepo>(repo));
    }

    public static RecentRepo repo(Object owner) {
        WeakReference<RecentRepo> reference = REPOS.get(owner);
        return reference == null ? null : reference.get();
    }

    public static List<ItemStack> items(Object owner) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (!Config.completedCraftRows || minecraft.theWorld != world
            || minecraft.currentScreen != owner
            || !(owner instanceof GuiMEMonitorable gui)
            || gui.inventorySlots.windowId != windowId) {
            return Collections.emptyList();
        }
        return HISTORY.newestFirst(System.nanoTime());
    }

    public static void receive(CompletedCraftsPacket packet) {
        resetWorld();
        if (packet.sequence <= lastSequence || !Config.completedCraftRows) return;
        PENDING.addLast(new Pending(packet, System.nanoTime()));
        while (PENDING.size() > 64) PENDING.removeFirst();
        applyPending();
    }

    private static void resetWorld() {
        Object current = Minecraft.getMinecraft().theWorld;
        if (world == current) return;
        world = current;
        HISTORY.clear();
        PENDING.clear();
        REPOS.clear();
        lastSequence = 0;
        gridId = 0;
        windowId = -1;
    }

    @SubscribeEvent
    public void opened(GuiOpenEvent event) {
        GuiScreen old = Minecraft.getMinecraft().currentScreen;
        if (event.gui == old) return; // Resize/reinitialization is not a terminal close.
        boolean navigating = craftingScreen(event.gui) && (old instanceof GuiMEMonitorable || craftingScreen(old));
        boolean returning = event.gui instanceof GuiMEMonitorable && craftingScreen(old);
        if (navigating || returning) return;
        HISTORY.clear();
        windowId = -1;
        gridId = 0;
        refresh(old, false);
        // A notification can arrive before its GUI opens. Keep that short-lived queue
        // for a new terminal; applyPending still checks window, dimension and sequence.
        if (!(event.gui instanceof GuiMEMonitorable)) PENDING.clear();
    }

    private static boolean craftingScreen(GuiScreen gui) {
        return gui instanceof GuiCraftAmount || gui instanceof GuiCraftConfirm
            || gui instanceof GuiCraftingCPU
            || gui instanceof GuiCraftingStatus;
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        resetWorld();
        Minecraft minecraft = Minecraft.getMinecraft();
        if (HISTORY.expire(System.nanoTime())) refresh(minecraft.currentScreen, false);
        if (!Config.completedCraftRows) {
            if (wasEnabled) {
                HISTORY.clear();
                PENDING.clear();
                refresh(minecraft.currentScreen, false);
            }
            wasEnabled = false;
            return;
        }
        wasEnabled = true;
        applyPending();
    }

    private static void applyPending() {
        Minecraft minecraft = Minecraft.getMinecraft();
        long now = System.nanoTime();
        boolean changed = false;
        boolean newItems = false;
        for (java.util.Iterator<Pending> iterator = PENDING.iterator(); iterator.hasNext();) {
            Pending pending = iterator.next();
            CompletedCraftsPacket packet = pending.packet;
            if (packet.sequence <= lastSequence || now - pending.receivedAt >= 3_000_000_000L) {
                iterator.remove();
                continue;
            }
            if (minecraft.thePlayer == null || minecraft.thePlayer.dimension != packet.dimension
                || !(minecraft.currentScreen instanceof GuiMEMonitorable gui)
                || gui.inventorySlots.windowId != packet.windowId) continue;
            if (gridId != packet.gridId) HISTORY.clear();
            gridId = packet.gridId;
            windowId = packet.windowId;
            lastSequence = packet.sequence;
            for (ItemStack item : packet.items) HISTORY.add(new StackKey(item), item, now);
            changed = true;
            newItems |= !packet.items.isEmpty();
            iterator.remove();
        }
        if (changed) refresh(minecraft.currentScreen, newItems);
    }

    private static void refresh(Object owner, boolean scrollToTop) {
        RecentRepo repo = repo(owner);
        if (repo != null) repo.ae2cf$refresh();
        if (owner instanceof RecentTerminalLayout layout) layout.ae2cf$refreshLayout(scrollToTop);
    }

    private static final class Pending {

        final CompletedCraftsPacket packet;
        final long receivedAt;

        Pending(CompletedCraftsPacket value, long now) {
            packet = value;
            receivedAt = now;
        }
    }
}
