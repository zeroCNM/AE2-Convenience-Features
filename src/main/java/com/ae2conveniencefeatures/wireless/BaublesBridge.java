package com.ae2conveniencefeatures.wireless;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;

import baubles.api.BaublesApi;

/** This class is reached only after a Loader check; no Baubles type escapes its signatures. */
final class BaublesBridge {

    private BaublesBridge() {}

    static IInventory inventory(EntityPlayer player) {
        return BaublesApi.getBaubles(player);
    }
}
