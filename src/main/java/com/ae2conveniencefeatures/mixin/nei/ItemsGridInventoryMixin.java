package com.ae2conveniencefeatures.mixin.nei;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.ae2conveniencefeatures.client.wireless.WirelessClient;

import codechicken.nei.ItemsGrid;

@Mixin(value = ItemsGrid.class, remap = false)
public abstract class ItemsGridInventoryMixin {

    @Inject(method = "draw(II)V", at = @At("TAIL"))
    private void ae2cf$drawInventory(int mouseX, int mouseY, CallbackInfo callback) {
        WirelessClient.draw((ItemsGrid<?, ?>) (Object) this);
    }
}
