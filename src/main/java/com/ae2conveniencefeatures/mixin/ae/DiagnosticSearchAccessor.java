package com.ae2conveniencefeatures.mixin.ae;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import appeng.client.gui.implementations.GuiCraftingDiagnosticTerminal;
import appeng.client.gui.widgets.MEGuiTextField;

@Mixin(value = GuiCraftingDiagnosticTerminal.class, remap = false)
public interface DiagnosticSearchAccessor {

    @Accessor("searchField")
    MEGuiTextField ae2cf$getSearchField();
}
