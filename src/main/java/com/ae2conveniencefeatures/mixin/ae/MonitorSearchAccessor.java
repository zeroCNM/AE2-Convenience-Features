package com.ae2conveniencefeatures.mixin.ae;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import appeng.client.gui.implementations.GuiMEMonitorable;
import appeng.client.gui.widgets.MEGuiTextField;

@Mixin(value = GuiMEMonitorable.class, remap = false)
public interface MonitorSearchAccessor {

    @Accessor("searchField")
    MEGuiTextField ae2cf$getSearchField();
}
