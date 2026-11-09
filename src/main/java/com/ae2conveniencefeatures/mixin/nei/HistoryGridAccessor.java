package com.ae2conveniencefeatures.mixin.nei;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import codechicken.nei.AbstractSubpanel;
import codechicken.nei.ItemsGrid;

/** Read-only access to the verified NEI 2.8.101 subpanel field; no public getter exists. */
@Mixin(value = AbstractSubpanel.class, remap = false)
public interface HistoryGridAccessor {

    @Accessor("grid")
    ItemsGrid<?, ?> ae2cf$getGrid();
}
