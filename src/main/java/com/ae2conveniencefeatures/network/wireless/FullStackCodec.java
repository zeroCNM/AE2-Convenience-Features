package com.ae2conveniencefeatures.network.wireless;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

/** Preserve complete NBT even for items whose getShareTag() is false. Counts are never client-authoritative. */
public final class FullStackCodec {

    private FullStackCodec() {}

    public static void write(ByteBuf buffer, ItemStack item) {
        if (item == null || item.getItem() == null) throw new IllegalArgumentException("Missing wireless item");
        ItemStack copy = item.copy();
        copy.stackSize = 1;
        ByteBufUtils.writeTag(buffer, copy.writeToNBT(new NBTTagCompound()));
    }

    public static ItemStack read(ByteBuf buffer) {
        NBTTagCompound tag = ByteBufUtils.readTag(buffer);
        ItemStack item = tag == null ? null : ItemStack.loadItemStackFromNBT(tag);
        if (item == null || item.getItem() == null) throw new IllegalArgumentException("Invalid wireless item");
        item.stackSize = 1;
        return item;
    }
}
