package com.ae2conveniencefeatures.network.wireless;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.fluids.FluidStack;

import com.ae2conveniencefeatures.wireless.WirelessTarget;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

final class WirelessTargetCodec {

    // Leave headroom below Minecraft's C17 custom payload limit, including the Forge discriminator.
    static final int CLIENT_PACKET_BUDGET = 16 * 1024;
    private static final String FLUID = "ae2cfFluid";

    private WirelessTargetCodec() {}

    static void write(ByteBuf buffer, WirelessTarget target) {
        if (target.isFluid()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setTag(
                FLUID,
                target.fluid()
                    .writeToNBT(new NBTTagCompound()));
            ByteBufUtils.writeTag(buffer, tag);
        } else {
            FullStackCodec.write(buffer, target.item());
        }
    }

    static WirelessTarget read(ByteBuf buffer) {
        NBTTagCompound tag = ByteBufUtils.readTag(buffer);
        // Ordinary item packets keep their existing NBT encoding.
        if (tag != null && tag.hasKey(FLUID, NBT.TAG_COMPOUND) && !tag.hasKey("id")) {
            FluidStack fluid = FluidStack.loadFluidStackFromNBT(tag.getCompoundTag(FLUID));
            return fluid == null ? null : WirelessTarget.fluid(fluid);
        }
        ItemStack item = FullStackCodec.readItem(tag);
        return item == null ? null : WirelessTarget.item(item);
    }

    static byte[] encode(WirelessTarget target, int budget) {
        if (target == null) return null;
        if (!target.isFluid()) {
            Item item = target.item()
                .getItem();
            if (item == null || Item.getItemById(Item.getIdFromItem(item)) != item) return null;
        }
        ByteBuf entry = Unpooled.buffer();
        try {
            write(entry, target);
            if (entry.readableBytes() > budget) return null;
            byte[] bytes = new byte[entry.readableBytes()];
            entry.readBytes(bytes);
            return bytes;
        } catch (RuntimeException invalidStack) {
            // An item with NBT that Forge cannot encode must not block the rest of a page.
            return null;
        } finally {
            entry.release();
        }
    }
}
