package com.ae2conveniencefeatures.mixin.nei;

import java.awt.Point;

import net.minecraft.client.gui.inventory.GuiContainer;

import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.ae2conveniencefeatures.client.wireless.WirelessClient;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.guihook.GuiContainerManager;

/** Own only eligible NEI middle presses before any onMouseClicked/mouseClicked notification. */
@Mixin(value = GuiContainerManager.class, remap = false, priority = 1100)
public abstract class ContainerMiddleClickMixin {

    @Shadow
    public GuiContainer window;
    @Shadow
    private int clickHandled;
    @Unique
    private boolean ae2cf$ownsMiddle;

    @Inject(method = "load()V", at = @At("HEAD"))
    private void ae2cf$reset(CallbackInfo callback) {
        ae2cf$ownsMiddle = false;
    }

    // NEI 2.8.171 routes all GUI mouse input through this method before GuiRecipe's
    // own widgets, key-bound crafting, and click notifications. Older NEI lacks it;
    // the manager/recipe click hooks below remain the fallback on that version.
    @Dynamic("Added in the current GTNH NEI; absent in the older supported compile baseline")
    @Inject(method = "handleMouseHotkey()Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void ae2cf$earlyMouse(CallbackInfoReturnable<Boolean> callback) {
        if (Mouse.getEventButton() != 2) return;
        if (!Mouse.getEventButtonState()) {
            if (ae2cf$ownsMiddle) {
                ae2cf$ownsMiddle = false;
                callback.setReturnValue(true);
            }
            return;
        }
        Point mouse = GuiDraw.getMousePosition();
        if (ae2cf$ownsMiddle || WirelessClient.craftClick(window, mouse.x, mouse.y, 2)) {
            ae2cf$ownsMiddle = true;
            callback.setReturnValue(true);
        }
    }

    @Inject(method = "mouseClicked(III)Z", at = @At("HEAD"), cancellable = true)
    private void ae2cf$craft(int x, int y, int button, CallbackInfoReturnable<Boolean> callback) {
        if (button != 2) return;
        if (ae2cf$ownsMiddle || WirelessClient.craftClick(window, x, y, button)) {
            ae2cf$ownsMiddle = true;
            clickHandled |= 1 << button; // Preserve NEI's matching overrideMouseUp bookkeeping.
            callback.setReturnValue(true);
        }
    }

    @Inject(method = "mouseUp(III)V", at = @At("HEAD"), cancellable = true)
    private void ae2cf$release(int x, int y, int button, CallbackInfo callback) {
        if (button == 2 && ae2cf$ownsMiddle) {
            ae2cf$ownsMiddle = false;
            callback.cancel(); // Other handlers did not receive this press, so do not notify its release.
        }
    }

    @Inject(method = "mouseDragged(IIIJ)V", at = @At("HEAD"), cancellable = true)
    private void ae2cf$drag(int x, int y, int button, long elapsed, CallbackInfo callback) {
        if (button == 2 && ae2cf$ownsMiddle) callback.cancel();
    }
}
