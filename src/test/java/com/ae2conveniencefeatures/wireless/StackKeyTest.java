package com.ae2conveniencefeatures.wireless;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
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

        StackKeyCache keys = new StackKeyCache();
        CountingTag measured = new CountingTag();
        measured.setTag("nested", nested.copy());
        measured.setByteArray("payload", new byte[1024]);
        ItemStack mutable = new ItemStack(item, 1, 2);
        mutable.setTagCompound(measured);
        StackKey cached = keys.key(mutable);
        for (int i = 0; i < 5000; i++) {
            if (keys.key(mutable) != cached) throw new AssertionError("stable item must reuse immutable identity");
        }
        check(measured.copies == 1, "5000 unchanged draw/tick lookups copy root NBT only once");
        mutable.stackSize = 64;
        check(keys.key(mutable) == cached, "changing count does not invalidate identity cache");
        measured.getCompoundTag("nested")
            .setString("owner", "Carol");
        StackKey changed = keys.key(mutable);
        check(changed != cached && !changed.equals(cached), "nested mutation invalidates cached identity");
        check(!cached.matches(mutable), "snapshot comparison detects deep NBT edits without a new key");
        check(measured.copies == 2, "one NBT mutation takes exactly one new deep snapshot");
        mutable.setItemDamage(3);
        check(keys.key(mutable) != changed, "metadata mutation invalidates cached identity");
        mutable.setTagCompound(null);
        StackKey withoutTag = keys.key(mutable);
        check(withoutTag.matches(mutable), "removing tag invalidates and matches the new identity");
        mutable.setTagCompound(new NBTTagCompound());
        check(keys.key(mutable) != withoutTag, "absent NBT and empty NBT retain distinct identities");
        ItemStack equalCopy = mutable.copy();
        check(
            keys.key(equalCopy)
                .equals(keys.key(mutable)),
            "distinct equal stacks map to equal immutable keys");
        check(
            !keys.key(mutable)
                .matches(new ItemStack(new Item(), 1, 3)),
            "comparison includes item identity");
        check(
            !keys.key(mutable)
                .matches(null),
            "null mutable input never matches");
        StackKey beforeClear = keys.key(mutable);
        keys.clear();
        check(keys.key(mutable) != beforeClear, "GUI close releases the key cache");
        System.out.println("PASS: " + checks + " real Minecraft item/NBT identity checks");
    }

    private static final class CountingTag extends NBTTagCompound {

        int copies;

        @Override
        public NBTBase copy() {
            copies++;
            return super.copy();
        }
    }
}
