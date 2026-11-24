package com.ae2conveniencefeatures.client.wireless;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.ae2conveniencefeatures.wireless.WirelessTarget;

import codechicken.nei.recipe.StackInfo;

final class NeiTarget {

    private NeiTarget() {}

    static WirelessTarget of(ItemStack stack) {
        // Older NEI lacks isFluidDisplayItem. Identify the display items without linking
        // optional classes or confusing a real bucket/cell (also understood by NEI) with a fluid.
        String type = stack.getItem()
            .getClass()
            .getName();
        switch (type) {
            case "codechicken.nei.item.ItemFluidDisplay":
            case "gregtech.common.items.ItemFluidDisplay":
            case "gregtech.common.items.GT_FluidDisplayItem":
                return asFluid(StackInfo.getFluid(stack));
            case "com.glodblock.github.common.item.ItemFluidDrop":
            case "com.glodblock.github.common.item.ItemFluidPacket":
                if (stack.hasTagCompound() && stack.getTagCompound()
                    .getBoolean("DisplayOnly")) {
                    return asFluid(StackInfo.getFluid(stack));
                }
                break;
            default:
                break;
        }
        return WirelessTarget.item(stack);
    }

    private static WirelessTarget asFluid(FluidStack fluid) {
        return fluid == null ? null : WirelessTarget.fluid(fluid);
    }
}
