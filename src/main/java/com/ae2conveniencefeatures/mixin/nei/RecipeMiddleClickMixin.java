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
    private boolean ae2cf$ownsMiddle;

    @Inject(method = "mouseClicked(III)V", at = @At("HEAD"), cancellable = true)
    private void ae2cf$craft(int x, int y, int button, CallbackInfo callback) {
        if (button == 2
            && (ae2cf$ownsMiddle || WirelessClient.craftClick((GuiRecipe<?>) (Object) this, x, y, button))) {
            ae2cf$ownsMiddle = true;
            callback.cancel();
        }
    }

    @Inject(method = "mouseMovedOrUp(III)V", at = @At("HEAD"), cancellable = true)
    private void ae2cf$release(int x, int y, int button, CallbackInfo callback) {
        if (button == 2 && ae2cf$ownsMiddle) {
            ae2cf$ownsMiddle = false;
            callback.cancel();
        }
    }

    @Inject(method = "mouseClickMove(IIIJ)V", at = @At("HEAD"), cancellable = true)
    private void ae2cf$drag(int x, int y, int button, long elapsed, CallbackInfo callback) {
        if (button == 2 && ae2cf$ownsMiddle) callback.cancel();
    }

    @Inject(method = "initGui()V", at = @At("HEAD"))
    private void ae2cf$reset(CallbackInfo callback) {
        ae2cf$ownsMiddle = false;
    }
}
