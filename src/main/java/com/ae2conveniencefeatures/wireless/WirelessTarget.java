package com.ae2conveniencefeatures.wireless;

import java.util.Objects;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/** Immutable NEI selection: an exact item or an exact fluid, without display/recipe quantity. */
public final class WirelessTarget {

    private final ItemStack item;
    private final StackKey itemKey;
    private final FluidStack fluid;

    private WirelessTarget(ItemStack item, FluidStack fluid) {
        this.item = item;
        this.fluid = fluid;
        itemKey = item == null ? null : new StackKey(item);
    }

    public static WirelessTarget item(ItemStack stack) {
        ItemStack copy = stack.copy();
        copy.stackSize = 1;
        return new WirelessTarget(copy, null);
    }

    public static WirelessTarget fluid(FluidStack stack) {
        FluidStack copy = stack.copy();
        copy.amount = 1;
        return new WirelessTarget(null, copy);
    }

    public boolean isFluid() {
        return fluid != null;
    }

    public ItemStack item() {
        return item == null ? null : item.copy();
    }

    public FluidStack fluid() {
        return fluid == null ? null : fluid.copy();
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof WirelessTarget target)) return false;
        return fluid == null ? itemKey.equals(target.itemKey) : fluid.isFluidEqual(target.fluid);
    }

    @Override
    public int hashCode() {
        return fluid == null ? itemKey.hashCode()
            : Objects.hash(
                fluid.getFluid()
                    .getName(),
                fluid.tag);
    }
}
