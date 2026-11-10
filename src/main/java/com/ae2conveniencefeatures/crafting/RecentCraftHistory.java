package com.ae2conveniencefeatures.crafting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One terminal visit. New orders never extend an already visible row's one-minute deadline. */
public final class RecentCraftHistory<K, V> {

    public static final long LIFETIME_NANOS = 60_000_000_000L;
    public static final int MAX_ITEMS = 256;
    private final LinkedHashMap<K, V> entries = new LinkedHashMap<K, V>();
    private long openedAt;
    private boolean active;

    public void add(K key, V value, long now) {
        expire(now);
        if (!active) {
            openedAt = now;
            active = true;
        }
        entries.remove(key);
        entries.put(key, value);
        while (entries.size() > MAX_ITEMS) entries.remove(
            entries.keySet()
                .iterator()
                .next());
    }

    public boolean expire(long now) {
        if (active && now - openedAt >= LIFETIME_NANOS) {
            clear();
            return true;
        }
        return false;
    }

    public List<V> newestFirst(long now) {
        expire(now);
        List<V> result = new ArrayList<V>(entries.values());
        Collections.reverse(result);
        return result;
    }

    public void clear() {
        entries.clear();
        active = false;
    }

    /** Move existing display references, pad the last recent row, leave the normal order intact. */
    public static <T> int prepend(List<T> view, List<T> recent, int columns) {
        if (columns <= 0 || recent.isEmpty()) return 0;
        Map<T, T> unique = new LinkedHashMap<T, T>();
        for (T item : recent) if (item != null) unique.put(item, item);
        if (unique.isEmpty()) return 0;
        view.removeIf(unique::containsKey);
        List<T> prefix = new ArrayList<T>(unique.values());
        int slots = (prefix.size() + columns - 1) / columns * columns;
        while (prefix.size() < slots) prefix.add(null);
        view.addAll(0, prefix);
        return slots;
    }

    public static int visibleRows(int prefixSlots, int columns, int scroll, int visibleSlots) {
        if (columns <= 0) return 0;
        return Math.min((visibleSlots + columns - 1) / columns, Math.max(0, prefixSlots / columns - scroll));
    }
}
