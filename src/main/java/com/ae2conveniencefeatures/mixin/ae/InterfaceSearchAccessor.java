package com.ae2conveniencefeatures.mixin.ae;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import appeng.client.gui.implementations.GuiInterfaceTerminal;
import appeng.client.gui.widgets.MEGuiTextField;

@Mixin(value = GuiInterfaceTerminal.class, remap = false)
public interface InterfaceSearchAccessor {

    @Accessor("searchFieldOutputs")
    MEGuiTextField ae2cf$getOutputs();

    @Accessor("searchFieldInputs")
    MEGuiTextField ae2cf$getInputs();

    @Accessor("searchFieldNames")
    MEGuiTextField ae2cf$getNames();
}
