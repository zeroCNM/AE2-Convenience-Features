package com.ae2conveniencefeatures.wireless;

import java.util.Objects;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** An immutable identity including metadata and the complete NBT, but excluding count. */
public final class StackKey {

    private final Item item;
    private final int damage;
    private final NBTTagCompound tag;
    private final int hash;

    public StackKey(ItemStack stack) {
        item = stack.getItem();
        damage = stack.getItemDamage();
        tag = stack.hasTagCompound() ? (NBTTagCompound) stack.getTagCompound()
            .copy() : null;
        hash = Objects.hash(item, damage, tag);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof StackKey)) return false;
        StackKey key = (StackKey) other;
        return item == key.item && damage == key.damage && Objects.equals(tag, key.tag);
    }

    @Override
    public int hashCode() {
        return hash;
    }
}
