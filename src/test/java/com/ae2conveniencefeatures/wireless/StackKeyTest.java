package com.ae2conveniencefeatures.wireless;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** Runs against the real deobfuscated 1.7.10 classes; checks full/deep NBT identity. */
public final class StackKeyTest {

    private static int checks;

    private static void check(boolean condition, String name) {
        checks++;
        if (!condition) throw new AssertionError(name);
    }

    public static void main(String[] args) {
        Item item = new Item();
        ItemStack first = new ItemStack(item, 1, 2);
        ItemStack same = new ItemStack(item, 64, 2);
        check(new StackKey(first).equals(new StackKey(same)), "stack size is not item identity");
        check(new StackKey(first).hashCode() == new StackKey(same).hashCode(), "equal keys have equal hash");
        check(!new StackKey(first).equals(new StackKey(new ItemStack(item, 1, 3))), "metadata is significant");
        check(
            !new StackKey(first).equals(new StackKey(new ItemStack(new Item(), 1, 2))),
            "item identity is significant");
        NBTTagCompound nested = new NBTTagCompound();
        nested.setString("owner", "Alice");
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("mode", 4);
        tag.setTag("nested", nested);
        first.setTagCompound(tag);
        same.setTagCompound((NBTTagCompound) tag.copy());
        check(new StackKey(first).equals(new StackKey(same)), "equal full NBT matches");
        StackKey immutable = new StackKey(first);
        Map<StackKey, Integer> cache = new HashMap<StackKey, Integer>();
        cache.put(immutable, 42);
        tag.setInteger("mode", 5);
        check(!immutable.equals(new StackKey(first)), "changed NBT does not match original key");
        check(cache.get(new StackKey(same)) == 42, "NBT mutation cannot corrupt the cache's hash key");
        first.setTagCompound(
            (NBTTagCompound) same.getTagCompound()
                .copy());
        first.getTagCompound()
            .getCompoundTag("nested")
            .setString("owner", "Bob");
        check(!immutable.equals(new StackKey(first)), "nested NBT differences are significant");
        check(immutable.equals(new StackKey(same)), "nested NBT snapshot is a deep copy");
        System.out.println("PASS: " + checks + " real Minecraft item/NBT identity checks");
    }
}
