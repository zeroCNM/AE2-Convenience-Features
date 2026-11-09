package com.ae2conveniencefeatures.client.wireless;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import com.ae2conveniencefeatures.Config;
import com.ae2conveniencefeatures.mixin.ae.DiagnosticSearchAccessor;
import com.ae2conveniencefeatures.mixin.ae.InterfaceSearchAccessor;
import com.ae2conveniencefeatures.mixin.ae.LevelSearchAccessor;
import com.ae2conveniencefeatures.mixin.ae.MonitorSearchAccessor;

import appeng.client.gui.widgets.MEGuiTextField;
import codechicken.nei.LayoutManager;
import codechicken.nei.guihook.GuiContainerManager;

final class SearchShortcut {

    private SearchShortcut() {}

    static boolean keyTyped(GuiContainer gui, int keyCode) {
        if (Config.searchKeyCode == 0 || keyCode != Config.searchKeyCode
            || LayoutManager.searchField != null && LayoutManager.searchField.focused()) return false;
        MEGuiTextField field;
        if (gui instanceof MonitorSearchAccessor search) {
            field = search.ae2cf$getSearchField();
        } else if (gui instanceof InterfaceSearchAccessor search) {
            if (search.ae2cf$getInputs()
                .isFocused()
                || search.ae2cf$getNames()
                    .isFocused())
                return false;
            field = search.ae2cf$getOutputs();
        } else if (gui instanceof LevelSearchAccessor search) {
            if (search.ae2cf$getNames()
                .isFocused()) return false;
            field = search.ae2cf$getOutputs();
        } else if (gui instanceof DiagnosticSearchAccessor search) {
            field = search.ae2cf$getSearchField();
        } else return false;
        if (field == null || field.isFocused()) return false;
        ItemStack item = GuiContainerManager.getStackMouseOver(gui);
        if (item == null) return false;
        String name = item.getDisplayName();
        if (name == null || name.isEmpty()) return false;
        field.setText(name);
        // The first NEI input handler consumes only an actual completed search. Other F-key
        // handlers do not run a second action; typing into focused text fields remains normal.
        return true;
    }
}
