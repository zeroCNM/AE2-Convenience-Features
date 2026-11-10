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

/** NEI 2.8.101 recipe body. Absent on newer NEI, which uses RecipeWidget instead. */
@Pseudo
@Mixin(targets = "codechicken.nei.recipe.NEIRecipeWidget", remap = false)
public abstract class LegacyRecipeInventoryMixin implements RecipeInventorySource {

    @Shadow
    protected abstract List<PositionedStack> getInputs();

    @Shadow
    protected abstract List<PositionedStack> getOutputs();

    @Shadow
    protected abstract List<PositionedStack> getCatalysts();

    @Override
    public void ae2cf$collectItems(Consumer<PositionedStack> collector) {
        getInputs().forEach(collector);
        getOutputs().forEach(collector);
        // These are stacks inside the recipe, not GuiRecipeCatalyst's left-hand machine list.
        getCatalysts().forEach(collector);
    }

    @Inject(method = "drawItem(Lcodechicken/nei/PositionedStack;IIIZ)V", at = @At("TAIL"))
    private void ae2cf$drawStock(PositionedStack stack, int mouseX, int mouseY, int yShift, boolean input,
        CallbackInfo callback) {
        // NEI's recipe translation and scroll scissor are still active here.
        WirelessClient.drawRecipeItem(stack);
    }
}
