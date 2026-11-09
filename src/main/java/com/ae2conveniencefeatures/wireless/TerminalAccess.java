package com.ae2conveniencefeatures.wireless;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.ae2conveniencefeatures.AE2ConvenienceFeatures;

import appeng.api.AEApi;
import appeng.api.config.SecurityPermissions;
import appeng.api.features.IWirelessTermHandler;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.ISecurityGrid;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.tile.misc.TileSecurity;
import appeng.util.Platform;
import cpw.mods.fml.common.Loader;

public final class TerminalAccess {

    public final ItemStack stack;
    public final IWirelessTermHandler handler;
    public final int slot;
    public final boolean bauble;
    public final String encryptionKey;
    public final WirelessTerminalGuiObject object;
    public final boolean accessible;

    private TerminalAccess(ItemStack stack, IWirelessTermHandler handler, int slot, boolean bauble, String key,
        WirelessTerminalGuiObject object, boolean accessible) {
        this.stack = stack;
        this.handler = handler;
        this.slot = slot;
        this.bauble = bauble;
        this.encryptionKey = key;
        this.object = object;
        this.accessible = accessible;
    }

    public String clientIdentity() {
        return slot + ":" + Item.getIdFromItem(stack.getItem()) + ":" + stack.getItemDamage() + ":" + encryptionKey;
    }

    /** Client discovery reads item data only and NEVER constructs a network GUI object. */
    public static TerminalAccess findClientItem(EntityPlayer player) {
        return find(player, false);
    }

    public static TerminalAccess resolveServer(EntityPlayer player) {
        if (Platform.isClient()) return null;
        return find(player, true);
    }

    public static boolean hasBaubles() {
        return Loader.isModLoaded("Baubles") || Loader.isModLoaded("Baubles|Expanded");
    }

    public static IInventory baublesInventory(EntityPlayer player) {
        // The separate bridge class is never resolved/executed without the optional mod.
        return hasBaubles() ? BaublesBridge.inventory(player) : null;
    }

    private static TerminalAccess find(EntityPlayer player, boolean server) {
        if (player == null) return null;
        // Only the main 36 slots: never mistake Backhand's offhand slot for a main slot.
        for (int i = 0; i < Math.min(36, player.inventory.mainInventory.length); i++) {
            TerminalAccess found = candidate(player, player.inventory.mainInventory[i], i, false, server);
            if (found != null) return found;
        }
        IInventory inventory = baublesInventory(player);
        if (inventory != null) {
            for (int i = 0; i < inventory.getSizeInventory(); i++) {
                TerminalAccess found = candidate(
                    player,
                    inventory.getStackInSlot(i),
                    Platform.baublesSlotsOffset + i,
                    true,
                    server);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static TerminalAccess candidate(EntityPlayer player, ItemStack stack, int slot, boolean bauble,
        boolean server) {
        if (stack == null || !(stack.getItem() instanceof IWirelessTermHandler)) return null;
        IWirelessTermHandler handler = (IWirelessTermHandler) stack.getItem();
        try {
            if (!handler.canHandle(stack) || !AEApi.instance()
                .registries()
                .wireless()
                .isWirelessTerminal(stack)) {
                return null;
            }
            String key = handler.getEncryptionKey(stack);
            if (key == null || key.isEmpty()) return null;
            if (!server) return new TerminalAccess(stack, handler, slot, bauble, key, null, false);

            long encryption = Long.parseLong(key);
            if (!(AEApi.instance()
                .registries()
                .locatable()
                .getLocatableBy(encryption) instanceof TileSecurity)) {
                return null;
            }
            if (!handler.hasPower(player, 1000F, stack)) return null;
            // Reference: standard AE2 validation layering; this implementation is written for this feature.
            // GTNH 2.9 uses an encoded x for Baubles; y still records main(0)/Baubles(1) as specified.
            WirelessTerminalGuiObject object = new WirelessTerminalGuiObject(
                handler,
                stack,
                player,
                player.worldObj,
                slot,
                bauble ? 1 : 0,
                0);
            boolean allowed = object.rangeCheck() && permits(player, object.getGrid(), SecurityPermissions.EXTRACT);
            // Do not fall back to a different network when the preferred terminal fails range/security.
            return new TerminalAccess(stack, handler, slot, bauble, key, object, allowed);
        } catch (RuntimeException error) {
            AE2ConvenienceFeatures.LOG.debug("Wireless terminal validation rejected an unusable item", error);
            return null;
        }
    }

    public static boolean permits(EntityPlayer player, IGrid grid, SecurityPermissions permission) {
        if (grid == null) return false;
        ISecurityGrid security = grid.getCache(ISecurityGrid.class);
        return security != null && security.hasPermission(player, permission);
    }
}
