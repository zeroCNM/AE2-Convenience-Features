package com.ae2conveniencefeatures.mixin.nei;

import java.util.List;
import java.util.function.Consumer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.ae2conveniencefeatures.client.wireless.RecipeInventorySource;
import com.ae2conveniencefeatures.client.wireless.WirelessClient;

import codechicken.nei.PositionedStack;

/** NEI 2.8.171 recipe body. Read the active group member, including pinned/cycling ingredients. */
@Pseudo
@Mixin(targets = "codechicken.nei.recipe.widget.RecipeWidget", remap = false)
public abstract class RecipeInventoryMixin implements RecipeInventorySource {

    @Shadow
    protected abstract List<PositionedStack> getCyclingStacks();

    @Shadow
    protected abstract List<PositionedStack> getOutputs();

    @Override
    public void ae2cf$collectItems(Consumer<PositionedStack> collector) {
        getCyclingStacks().forEach(collector);
        getOutputs().forEach(collector);
    }

    @Inject(method = "drawItem(Lcodechicken/nei/PositionedStack;IIIZ)V", at = @At("TAIL"))
    private void ae2cf$drawStock(PositionedStack stack, int mouseX, int mouseY, int yShift, boolean input,
        CallbackInfo callback) {
        WirelessClient.drawRecipeItem(stack);
    }
}
