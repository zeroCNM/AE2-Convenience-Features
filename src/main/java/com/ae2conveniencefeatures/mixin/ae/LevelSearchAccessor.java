package com.ae2conveniencefeatures.mixin.ae;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

import appeng.client.gui.widgets.MEGuiTextField;

/** Optional integration; names and types verified in GTNH 2.9.0 AE2FC 1.5.88 and Pro 1.5.116. */
@Pseudo
@Mixin(targets = "com.glodblock.github.client.gui.GuiLevelTerminal", remap = false)
public interface LevelSearchAccessor {

    @Accessor("searchFieldOutputs")
    MEGuiTextField ae2cf$getOutputs();

    @Accessor("searchFieldNames")
    MEGuiTextField ae2cf$getNames();
}
