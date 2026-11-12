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

/** Own only eligible NEI middle/Space-left presses before click notifications. */
@Mixin(value = GuiContainerManager.class, remap = false, priority = 1100)
public abstract class ContainerMiddleClickMixin {

    @Shadow
    public GuiContainer window;
    @Shadow
    private int clickHandled;
    @Unique
    private int ae2cf$ownedButtons;

    @Inject(method = "load()V", at = @At("HEAD"))
    private void ae2cf$reset(CallbackInfo callback) {
        ae2cf$ownedButtons = 0;
    }

    // NEI 2.8.171 routes all GUI mouse input through this method before GuiRecipe's
    // own widgets, key-bound crafting, and click notifications. Older NEI lacks it;
    // the manager/recipe click hooks below remain the fallback on that version.
    @Dynamic("Added in the current GTNH NEI; absent in the older supported compile baseline")
    @Inject(method = "handleMouseHotkey()Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void ae2cf$earlyMouse(CallbackInfoReturnable<Boolean> callback) {
        int button = Mouse.getEventButton();
        if (button != 0 && button != 2) return;
        int mask = 1 << button;
        if (!Mouse.getEventButtonState()) {
            if ((ae2cf$ownedButtons & mask) != 0) {
                ae2cf$ownedButtons &= ~mask;
                callback.setReturnValue(true);
            }
            return;
        }
        Point mouse = GuiDraw.getMousePosition();
        if ((ae2cf$ownedButtons & mask) != 0 || WirelessClient.priorityClick(window, mouse.x, mouse.y, button)) {
            ae2cf$ownedButtons |= mask;
            callback.setReturnValue(true);
        }
    }

    @Inject(method = "mouseClicked(III)Z", at = @At("HEAD"), cancellable = true)
    private void ae2cf$shortcut(int x, int y, int button, CallbackInfoReturnable<Boolean> callback) {
        if (button != 0 && button != 2) return;
        int mask = 1 << button;
        if ((ae2cf$ownedButtons & mask) != 0 || WirelessClient.priorityClick(window, x, y, button)) {
            ae2cf$ownedButtons |= mask;
            clickHandled |= 1 << button; // Preserve NEI's matching overrideMouseUp bookkeeping.
            callback.setReturnValue(true);
        }
    }

    @Inject(method = "mouseUp(III)V", at = @At("HEAD"), cancellable = true)
    private void ae2cf$release(int x, int y, int button, CallbackInfo callback) {
        if ((button == 0 || button == 2) && (ae2cf$ownedButtons & 1 << button) != 0) {
            ae2cf$ownedButtons &= ~(1 << button);
            callback.cancel(); // Other handlers did not receive this press, so do not notify its release.
        }
    }

    @Inject(method = "mouseDragged(IIIJ)V", at = @At("HEAD"), cancellable = true)
    private void ae2cf$drag(int x, int y, int button, long elapsed, CallbackInfo callback) {
        if ((button == 0 || button == 2) && (ae2cf$ownedButtons & 1 << button) != 0) callback.cancel();
    }
}
