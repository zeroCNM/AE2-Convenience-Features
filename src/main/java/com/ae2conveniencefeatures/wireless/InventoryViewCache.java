package com.ae2conveniencefeatures.wireless;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure client cache state, separated from Minecraft so stale-response/expiry behavior can be tested. */
public final class InventoryViewCache<K> {

    // Fixed 500 ms TTL: allows normal server/network latency without querying during rendering.
    public static final long TTL_NANOS = 500_000_000L;
    // Presentation only: keep the last number while a refresh is in flight, not an empty frame.
    // A prolonged connection failure still removes it. Actions always use the strict 500 ms peek.
    public static final long DISPLAY_HOLD_NANOS = 10_000_000_000L;
    private final Map<K, Stock> entries = new HashMap<K, Stock>();
    private final Map<K, Stock> displayed = new HashMap<K, Stock>();
    private Set<K> visible = new LinkedHashSet<K>();
    private Object context;
    private long sequence;
    private long pending = -1;
    private long pendingAt;
    private long gridId;
    private long minimumRevision;
    private long lastRevision = -1;

    public static final class Stock {

        public final long count;
        public final boolean craftable;
        public final String display;
        public final long receivedAt;

        public Stock(long count, boolean craftable, long receivedAt) {
            this.count = Math.max(0, count);
            this.craftable = craftable;
            this.receivedAt = receivedAt;
            display = formatCount(this.count);
        }
    }

    public boolean context(Object nextContext, Set<K> nextVisible) {
        if (!java.util.Objects.equals(context, nextContext)) {
            context = nextContext;
            visible = new LinkedHashSet<K>(nextVisible);
            gridId = 0;
            minimumRevision = 0;
            lastRevision = -1;
            invalidate(0);
            return true;
        }
        if (!visible.equals(nextVisible)) {
            visible = new LinkedHashSet<K>(nextVisible);
            entries.keySet()
                .retainAll(visible);
            displayed.keySet()
                .retainAll(visible);
            pending = -1;
            ++sequence;
        }
        // Only a terminal/world/GUI switch resets the client's terminal metadata. A changed
        // visible set rejects its old pending batch, but preserves numbers still on screen.
        return false;
    }

    public Stock peek(K key, long now) {
        Stock stock = entries.get(key);
        return stock != null && now - stock.receivedAt < TTL_NANOS ? stock : null;
    }

    public boolean needsRefresh(K key, long now) {
        Stock stock = entries.get(key);
        return stock == null || now - stock.receivedAt >= TTL_NANOS / 2;
    }

    public Stock peekForDisplay(K key, long now) {
        Stock stock = displayed.get(key);
        return stock != null && now - stock.receivedAt < DISPLAY_HOLD_NANOS ? stock : null;
    }

    public List<K> refreshCandidates(long now, int limit) {
        List<K> candidates = new ArrayList<K>();
        for (K key : visible) {
            if (needsRefresh(key, now)) candidates.add(key);
        }
        // Missing and oldest visible items go first. Repeated network revisions must not let
        // the first batch starve the history/bookmark panels or a large NEI item page.
        candidates.sort((left, right) -> {
            Stock a = displayed.get(left);
            Stock b = displayed.get(right);
            if (a == null) return b == null ? 0 : -1;
            if (b == null) return 1;
            return Long.compare(a.receivedAt, b.receivedAt);
        });
        return new ArrayList<K>(candidates.subList(0, Math.min(Math.max(0, limit), candidates.size())));
    }

    public boolean pending(long now) {
        if (pending != -1 && now - pendingAt >= 3_000_000_000L) pending = -1;
        return pending != -1;
    }

    public long begin(long now) {
        pending = ++sequence;
        pendingAt = now;
        return pending;
    }

    public boolean accept(long request, long grid, long revision, Map<K, Stock> snapshot) {
        if (request != pending || pending == -1) return false;
        pending = -1;
        if (grid != gridId) {
            entries.clear();
            displayed.clear();
            minimumRevision = 0;
            lastRevision = -1;
        }
        gridId = grid;
        if (revision < minimumRevision) return false;
        if (revision > lastRevision) entries.clear();
        lastRevision = revision;
        minimumRevision = revision;
        for (Map.Entry<K, Stock> row : snapshot.entrySet()) {
            if (visible.contains(row.getKey())) {
                entries.put(row.getKey(), row.getValue());
                displayed.put(row.getKey(), row.getValue());
            }
        }
        return true;
    }

    public void invalidate(long grid) {
        if (grid != 0 && grid != gridId) return;
        displayed.clear();
        refresh();
    }

    public void refresh() {
        entries.clear();
        pending = -1;
        ++sequence; // Never accept a reply that was already in flight at invalidation/switch time.
    }

    public void inventoryChanged(long grid, long revision) {
        if (grid != gridId || revision <= lastRevision) return;
        minimumRevision = Math.max(minimumRevision, revision);
        entries.clear();
        // Keep the pending request: its server snapshot may already include this change.
        // Revision checking rejects older data without starving replies on a busy/high-latency grid.
    }

    public static String formatCount(long count) {
        if (count < 1000) return Long.toString(Math.max(0, count));
        String[] suffixes = { "k", "M", "G", "T", "P", "E" };
        double value = count / 1000.0;
        int unit = 0;
        while (value >= 999.95 && unit < suffixes.length - 1) {
            value /= 1000;
            unit++;
        }
        long tenths = Math.round(value * 10);
        return tenths / 10 + "." + tenths % 10 + suffixes[unit];
    }
}
