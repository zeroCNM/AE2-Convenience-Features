package com.ae2conveniencefeatures.mixin;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.ae2conveniencefeatures.wireless.TerminalAccess;

import appeng.util.Platform;
import cpw.mods.fml.common.Loader;

/** AE2 977's stock encoded-slot lookup supports Expanded only. Add the same slot convention for plain Baubles. */
@Mixin(value = Platform.class, remap = false)
public abstract class PlatformBaublesMixin {

    @Inject(method = "getItemFromPlayerInventoryBySlotIndex", at = @At("HEAD"), cancellable = true)
    private static void ae2cf$plainBaubles(EntityPlayer player, int slot, CallbackInfoReturnable<ItemStack> callback) {
        if (slot < Platform.baublesSlotsOffset || !Loader.isModLoaded("Baubles")
            || Loader.isModLoaded("Baubles|Expanded")) return;
        IInventory inventory = TerminalAccess.baublesInventory(player);
        int index = slot - Platform.baublesSlotsOffset;
        callback.setReturnValue(
            inventory != null && index >= 0 && index < inventory.getSizeInventory() ? inventory.getStackInSlot(index)
                : null);
    }
}
