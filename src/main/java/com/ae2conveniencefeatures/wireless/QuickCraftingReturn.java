package com.ae2conveniencefeatures.wireless;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.container.PrimaryGui;

/** A shortcut starts outside an AE terminal, but its amount/confirmation flow still needs a return target. */
public final class QuickCraftingReturn extends PrimaryGui {

    public QuickCraftingReturn(ItemStack terminal) {
        super(null, terminal.copy(), null, ForgeDirection.UNKNOWN);
    }

    @Override
    public void open(EntityPlayer player) {
        // AE2 calls this after a successful submit and when returning from a crafting sub-GUI.
        // There was no original terminal GUI to reopen; close the server container normally.
        player.closeScreen();
    }
}
