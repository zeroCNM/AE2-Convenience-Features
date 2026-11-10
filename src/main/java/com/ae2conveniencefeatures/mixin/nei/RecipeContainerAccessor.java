package com.ae2conveniencefeatures.mixin.nei;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.scroll.ScrollContainer;

/** Read-only recipe body; the machine list lives in GuiRecipe.recipeCatalyst instead. */
@Mixin(value = GuiRecipe.class, remap = false)
public interface RecipeContainerAccessor {

    @Accessor("container")
    ScrollContainer ae2cf$getRecipeContainer();
}
