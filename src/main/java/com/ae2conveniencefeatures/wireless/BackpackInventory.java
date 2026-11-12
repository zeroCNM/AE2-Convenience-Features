package com.ae2conveniencefeatures.wireless;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

/** Normal backpack and hotbar only; never replaces occupied slots or uses armor/extra slots. */
public final class BackpackInventory {

    private BackpackInventory() {}

    private static int room(IInventory inventory, int slot, ItemStack item) {
        if (!inventory.isItemValidForSlot(slot, item)) return 0;
        int limit = Math.min(item.getMaxStackSize(), inventory.getInventoryStackLimit());
        ItemStack existing = inventory.getStackInSlot(slot);
        if (existing == null) return Math.max(0, limit);
        if (!existing.isItemEqual(item) || !ItemStack.areItemStackTagsEqual(existing, item)) return 0;
        return Math.max(0, Math.min(limit, existing.getMaxStackSize()) - existing.stackSize);
    }

    public static int capacity(IInventory inventory, ItemStack item) {
        if (item == null || item.getItem() == null) return 0;
        long total = 0;
        for (int slot = 0; slot < Math.min(36, inventory.getSizeInventory()); slot++) {
            total += room(inventory, slot, item);
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    /** Splits the actual extraction into legal stacks; item.stackSize retains only unplaced items. */
    public static void insert(IInventory inventory, ItemStack item) {
        if (item == null || item.getItem() == null || item.stackSize <= 0) return;
        // Merge exact matching stacks before filling empty slots, like normal inventory insertion.
        for (int pass = 0; pass < 2 && item.stackSize > 0; pass++) {
            for (int slot = 0; slot < Math.min(36, inventory.getSizeInventory()) && item.stackSize > 0; slot++) {
                ItemStack existing = inventory.getStackInSlot(slot);
                if ((existing == null) != (pass == 1)) continue;
                int amount = Math.min(item.stackSize, room(inventory, slot, item));
                if (amount <= 0) continue;
                ItemStack placed = existing == null ? item.copy() : existing.copy();
                placed.stackSize = (existing == null ? 0 : existing.stackSize) + amount;
                inventory.setInventorySlotContents(slot, placed);
                item.stackSize -= amount;
            }
        }
    }
}
