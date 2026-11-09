package com.ae2conveniencefeatures.client.wireless;

import net.minecraft.client.gui.inventory.GuiContainer;

import codechicken.nei.guihook.IContainerInputHandler;

final class WirelessInputHandler implements IContainerInputHandler {

    @Override
    public boolean mouseClicked(GuiContainer gui, int mousex, int mousey, int button) {
        return WirelessClient.click(gui, mousex, mousey, button);
    }

    @Override
    public boolean keyTyped(GuiContainer gui, char character, int key) {
        return SearchShortcut.keyTyped(gui, key);
    }

    @Override
    public boolean lastKeyTyped(GuiContainer gui, char character, int key) {
        return false;
    }

    @Override
    public boolean mouseScrolled(GuiContainer gui, int mousex, int mousey, int amount) {
        return false;
    }

    @Override
    public void onKeyTyped(GuiContainer gui, char character, int key) {}

    @Override
    public void onMouseClicked(GuiContainer gui, int mousex, int mousey, int button) {}

    @Override
    public void onMouseUp(GuiContainer gui, int mousex, int mousey, int button) {}

    @Override
    public void onMouseDragged(GuiContainer gui, int mousex, int mousey, int button, long time) {}

    @Override
    public void onMouseScrolled(GuiContainer gui, int mousex, int mousey, int amount) {}
}
