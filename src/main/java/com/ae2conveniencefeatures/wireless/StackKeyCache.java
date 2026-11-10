package com.ae2conveniencefeatures.wireless;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.item.ItemStack;

/** Client-thread lookup cache. ItemStack uses object identity; every hit checks current full NBT. */
public final class StackKeyCache {

    public static final int MAX_ENTRIES = 4096;
    private final Map<ItemStack, StackKey> keys = new WeakHashMap<>();

    public StackKey key(ItemStack item) {
        StackKey key = keys.get(item);
        if (key == null || !key.matches(item)) {
            key = new StackKey(item);
            if (keys.size() >= MAX_ENTRIES) keys.clear();
            keys.put(item, key);
        }
        return key;
    }

    public void clear() {
        keys.clear();
    }
}
