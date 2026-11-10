package com.ae2conveniencefeatures.client.wireless;

import java.util.function.Consumer;

import codechicken.nei.PositionedStack;

/** Current recipe-body stacks, excluding the separate recipe-catalyst machine list. */
public interface RecipeInventorySource {

    void ae2cf$collectItems(Consumer<PositionedStack> collector);
}
