package com.ae2conveniencefeatures.mixin.nei;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.ae2conveniencefeatures.client.wireless.WirelessClient;

import codechicken.nei.recipe.GuiRecipe;

/** Older NEI recipe widgets run before the base container's input dispatcher. */
@Mixin(value = GuiRecipe.class, priority = 1100)
public abstract class RecipeMiddleClickMixin {

    @Unique
    private int ae2cf$ownedButtons;

    @Inject(method = "mouseClicked(III)V", at = @At("HEAD"), cancellable = true)
    private void ae2cf$shortcut(int x, int y, int button, CallbackInfo callback) {
        if (button != 0 && button != 2) return;
        int mask = 1 << button;
        if ((ae2cf$ownedButtons & mask) != 0
            || WirelessClient.priorityClick((GuiRecipe<?>) (Object) this, x, y, button)) {
            ae2cf$ownedButtons |= mask;
            callback.cancel();
        }
    }

    @Inject(method = "mouseMovedOrUp(III)V", at = @At("HEAD"), cancellable = true)
    private void ae2cf$release(int x, int y, int button, CallbackInfo callback) {
        if ((button == 0 || button == 2) && (ae2cf$ownedButtons & 1 << button) != 0) {
            ae2cf$ownedButtons &= ~(1 << button);
            callback.cancel();
        }
    }

    @Inject(method = "mouseClickMove(IIIJ)V", at = @At("HEAD"), cancellable = true)
    private void ae2cf$drag(int x, int y, int button, long elapsed, CallbackInfo callback) {
        if ((button == 0 || button == 2) && (ae2cf$ownedButtons & 1 << button) != 0) callback.cancel();
    }

    @Inject(method = "initGui()V", at = @At("HEAD"))
    private void ae2cf$reset(CallbackInfo callback) {
        ae2cf$ownedButtons = 0;
    }
}
