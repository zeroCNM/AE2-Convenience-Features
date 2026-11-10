package com.ae2conveniencefeatures.crafting;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;

import com.ae2conveniencefeatures.Config;
import com.ae2conveniencefeatures.network.ModNetwork;
import com.ae2conveniencefeatures.network.crafting.CompletedCraftsPacket;
import com.ae2conveniencefeatures.wireless.StackKey;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.container.implementations.ContainerMEMonitorable;
import appeng.helpers.WirelessTerminalGuiObject;

/** Server thread only. Completed identities wait for a terminal on the same grid, then are consumed once. */
public final class CompletedCraftsService {

    private static final Map<String, List<Completed>> PENDING = new LinkedHashMap<String, List<Completed>>();
    private static final Map<IGrid, Long> GRIDS = new WeakHashMap<IGrid, Long>();
    private static final Map<EntityPlayerMP, Bound> BOUND = new WeakHashMap<EntityPlayerMP, Bound>();
    private static long gridSequence;
    private static long sequence;

    private CompletedCraftsService() {}

    public static void completed(IGrid grid, String player, ItemStack item) {
        if (!Config.completedCraftRows || grid == null
            || player == null
            || player.isEmpty()
            || item == null
            || item.getItem() == null) return;
        String owner = player.toLowerCase(Locale.ROOT);
        List<Completed> queue = PENDING.computeIfAbsent(owner, ignored -> new ArrayList<Completed>());
        StackKey key = new StackKey(item);
        queue.removeIf(
            previous -> previous.grid.get() == null || previous.grid.get() == grid && previous.key.equals(key));
        ItemStack identity = item.copy();
        identity.stackSize = 1;
        queue.add(new Completed(grid, identity, key));
        while (queue.size() > RecentCraftHistory.MAX_ITEMS) queue.remove(0);
        // Bound offline-player bookkeeping without retaining grids or chunks.
        while (PENDING.size() > 1024) PENDING.remove(
            PENDING.keySet()
                .iterator()
                .next());
    }

    public static void tick() {
        if (!Config.completedCraftRows) {
            clear();
            return;
        }
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) return;
        for (Object value : server.getConfigurationManager().playerEntityList) {
            EntityPlayerMP player = (EntityPlayerMP) value;
            if (!(player.openContainer instanceof ContainerMEMonitorable terminal)
                || !terminal.canInteractWith(player)) {
                BOUND.remove(player);
                continue;
            }
            IGrid grid = grid(terminal);
            if (grid == null) {
                BOUND.remove(player);
                continue;
            }
            String owner = PENDING.isEmpty() ? null
                : player.getCommandSenderName()
                    .toLowerCase(Locale.ROOT);
            List<Completed> queue = owner == null ? null : PENDING.get(owner);
            CompletedCraftsPacket packet = null;
            if (queue != null) {
                Iterator<Completed> iterator = queue.iterator();
                while (iterator.hasNext()) {
                    Completed item = iterator.next();
                    IGrid origin = item.grid.get();
                    if (origin == grid) {
                        if (packet == null) packet = new CompletedCraftsPacket();
                        packet.items.add(item.item);
                    }
                    if (origin == null || origin == grid) iterator.remove();
                }
                if (queue.isEmpty()) PENDING.remove(owner);
            }
            Bound previous = BOUND.get(player);
            if (packet == null && previous != null
                && previous.terminal.get() == terminal
                && previous.grid.get() == grid) continue;
            if (packet == null) packet = new CompletedCraftsPacket();
            packet.gridId = GRIDS.computeIfAbsent(grid, ignored -> ++gridSequence);
            packet.sequence = ++sequence;
            packet.dimension = player.dimension;
            packet.windowId = terminal.windowId;
            ModNetwork.CHANNEL.sendTo(packet, player);
            BOUND.put(player, new Bound(terminal, grid));
        }
    }

    private static IGrid grid(ContainerMEMonitorable terminal) {
        IGridNode node = terminal.getNetworkNode();
        if (node != null) return node.getGrid();
        // WirelessTerminalGuiObject is a portable host, not an IGridHost; networkNode is null.
        Object host = terminal.getTarget();
        return host instanceof WirelessTerminalGuiObject wireless ? wireless.getGrid() : null;
    }

    public static void clear() {
        PENDING.clear();
        GRIDS.clear();
        BOUND.clear();
        // Do not rewind sequence during a live client connection.
    }

    private static final class Completed {

        final WeakReference<IGrid> grid;
        final ItemStack item;
        final StackKey key;

        Completed(IGrid origin, ItemStack identity, StackKey identityKey) {
            grid = new WeakReference<IGrid>(origin);
            item = identity;
            key = identityKey;
        }
    }

    private static final class Bound {

        final WeakReference<ContainerMEMonitorable> terminal;
        final WeakReference<IGrid> grid;

        Bound(ContainerMEMonitorable current, IGrid origin) {
            terminal = new WeakReference<ContainerMEMonitorable>(current);
            grid = new WeakReference<IGrid>(origin);
        }
    }
}
