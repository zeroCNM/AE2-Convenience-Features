package com.ae2conveniencefeatures.wireless;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

/** Server-side selection and placement, limited to the normal 36 inventory slots. */
public final class HandInventory {

    private HandInventory() {}

    private static boolean empty(ItemStack stack) {
        return stack == null || stack.stackSize <= 0;
    }

    private static boolean matches(ItemStack stack, ItemStack target) {
        return !empty(stack) && stack.isItemEqual(target) && ItemStack.areItemStackTagsEqual(stack, target);
    }

    public static int selectExisting(IInventory inventory, int selected, ItemStack target) {
        int size = Math.min(36, inventory.getSizeInventory());
        if (selected < 0 || selected >= Math.min(9, size) || target == null) return -1;
        if (matches(inventory.getStackInSlot(selected), target)) return selected;
        for (int i = 0; i < Math.min(9, size); i++) {
            if (matches(inventory.getStackInSlot(i), target)) return i;
        }
        for (int i = 9; i < size; i++) {
            ItemStack existing = inventory.getStackInSlot(i);
            if (!matches(existing, target)) continue;
            int destination = emptyHotbar(inventory, selected);
            if (destination < 0) destination = selected;
            ItemStack displaced = inventory.getStackInSlot(destination);
            inventory.setInventorySlotContents(destination, existing);
            inventory.setInventorySlotContents(i, displaced);
            return destination;
        }
        return -1;
    }

    private static int emptyHotbar(IInventory inventory, int selected) {
        int size = Math.min(9, inventory.getSizeInventory());
        if (selected < 0 || selected >= size) return -1;
        if (empty(inventory.getStackInSlot(selected))) return selected;
        for (int i = 0; i < size; i++) {
            if (empty(inventory.getStackInSlot(i))) return i;
        }
        return -1;
    }

    private static int emptyStorage(IInventory inventory) {
        for (int i = 9; i < Math.min(36, inventory.getSizeInventory()); i++) {
            if (empty(inventory.getStackInSlot(i))) return i;
        }
        return -1;
    }

    public static boolean hasRoom(IInventory inventory, int selected) {
        return selected >= 0 && selected < Math.min(9, inventory.getSizeInventory())
            && (emptyHotbar(inventory, selected) >= 0 || emptyStorage(inventory) >= 0);
    }

    /** Takes ownership of the actual extracted stack only on success; never overwrites an item. */
    public static int place(IInventory inventory, int selected, ItemStack extracted) {
        if (empty(extracted)
            || extracted.stackSize > Math.min(extracted.getMaxStackSize(), inventory.getInventoryStackLimit())
            || !hasRoom(inventory, selected)) return -1;
        int slot = emptyHotbar(inventory, selected);
        if (slot < 0) {
            int storage = emptyStorage(inventory);
            if (storage < 0) return -1;
            inventory.setInventorySlotContents(storage, inventory.getStackInSlot(selected));
            slot = selected;
        }
        inventory.setInventorySlotContents(slot, extracted);
        return slot;
    }
}
