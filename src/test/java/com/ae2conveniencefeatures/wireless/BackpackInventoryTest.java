package com.ae2conveniencefeatures.wireless;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Random;
import java.util.Set;

import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** Real Minecraft stacks/inventories: multi-stack insertion must conserve items and isolate slots/NBT. */
public final class BackpackInventoryTest {

    private static final Item TARGET = new Item();
    private static final Item OTHER = new Item();
    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static InventoryBasic inventory() {
        return new InventoryBasic("backpack", true, 41);
    }

    private static InventoryBasic occupied() {
        InventoryBasic inventory = inventory();
        for (int slot = 0; slot < 36; slot++) inventory.setInventorySlotContents(slot, new ItemStack(OTHER, 64));
        return inventory;
    }

    private static ItemStack tagged(int count, String owner) {
        ItemStack item = new ItemStack(TARGET, count, 7);
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagCompound nested = new NBTTagCompound();
        nested.setString("owner", owner);
        tag.setTag("nested", nested);
        item.setTagCompound(tag);
        return item;
    }

    private static boolean matches(ItemStack a, ItemStack b) {
        return a != null && a.isItemEqual(b) && ItemStack.areItemStackTagsEqual(a, b);
    }

    private static int count(InventoryBasic inventory, ItemStack target) {
        int total = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack item = inventory.getStackInSlot(slot);
            if (matches(item, target)) total += item.stackSize;
        }
        return total;
    }

    private static boolean legalStacks(InventoryBasic inventory) {
        Set<ItemStack> identities = Collections.newSetFromMap(new IdentityHashMap<ItemStack, Boolean>());
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            ItemStack item = inventory.getStackInSlot(slot);
            if (item != null && (!identities.add(item) || item.stackSize < 1
                || item.stackSize > Math.min(item.getMaxStackSize(), inventory.getInventoryStackLimit()))) return false;
        }
        return true;
    }

    public static void main(String[] args) {
        ItemStack target = tagged(1, "Alice");
        InventoryBasic inventory = inventory();
        ItemStack armor = new ItemStack(OTHER, 12);
        inventory.setInventorySlotContents(38, armor);
        check(BackpackInventory.capacity(inventory, target) == 36 * 64, "all 36 normal slots counted, no armor");
        ItemStack extracted = tagged(36 * 64, "Alice");
        BackpackInventory.insert(inventory, extracted);
        check(extracted.stackSize == 0 && count(inventory, target) == 2304, "one click can fill more than one stack");
        check(legalStacks(inventory), "large extraction is split into independent legal stacks");
        check(inventory.getStackInSlot(38) == armor && armor.stackSize == 12, "armor/extra slots untouched");
        inventory.getStackInSlot(0)
            .getTagCompound()
            .getCompoundTag("nested")
            .setString("owner", "Bob");
        check(matches(inventory.getStackInSlot(1), target), "split stacks have independent nested NBT");

        inventory = occupied();
        inventory.setInventorySlotContents(5, tagged(50, "Alice"));
        inventory.setInventorySlotContents(10, null);
        ItemStack untouched = inventory.getStackInSlot(0);
        check(BackpackInventory.capacity(inventory, target) == 78, "partial stack plus empty slot capacity");
        extracted = tagged(200, "Alice");
        BackpackInventory.insert(inventory, extracted);
        check(extracted.stackSize == 122 && count(inventory, target) == 128, "overflow retained for return to AE");
        check(inventory.getStackInSlot(0) == untouched && untouched.stackSize == 64, "other items never replaced");
        check(BackpackInventory.capacity(inventory, target) == 0, "full inventory cannot request more");

        inventory = occupied();
        inventory.setInventorySlotContents(4, tagged(1, "Bob"));
        inventory.setInventorySlotContents(7, new ItemStack(TARGET, 1, 8));
        check(BackpackInventory.capacity(inventory, target) == 0, "different meta or nested NBT cannot merge");
        extracted = target.copy();
        BackpackInventory.insert(inventory, extracted);
        check(extracted.stackSize == 1 && count(inventory, target) == 0, "mismatching slots leave extraction intact");

        inventory = inventory();
        extracted = tagged(58, "Alice");
        BackpackInventory.insert(inventory, extracted);
        check(
            extracted.stackSize == 0 && count(inventory, target) == 58,
            "short stock inserts exactly what was extracted");
        check(
            inventory.getStackInSlot(0).stackSize == 58 && inventory.getStackInSlot(1) == null,
            "no invented full stacks");
        inventory = occupied();
        inventory.setInventorySlotContents(0, null);
        inventory.setInventorySlotContents(5, tagged(12, "Alice"));
        extracted = tagged(20, "Alice");
        BackpackInventory.insert(inventory, extracted);
        check(
            inventory.getStackInSlot(5).stackSize == 32 && inventory.getStackInSlot(0) == null,
            "merge before empty slots");

        inventory = new InventoryBasic("limited", true, 41) {

            @Override
            public int getInventoryStackLimit() {
                return 16;
            }
        };
        check(BackpackInventory.capacity(inventory, target) == 576, "inventory stack limit respected");
        extracted = tagged(600, "Alice");
        BackpackInventory.insert(inventory, extracted);
        check(
            extracted.stackSize == 24 && count(inventory, target) == 576 && legalStacks(inventory),
            "limited slots conserve overflow");
        ItemStack single = new ItemStack(new Item().setMaxStackSize(1), 40);
        inventory = inventory();
        check(BackpackInventory.capacity(inventory, single) == 36, "unstackable capacity is one per slot");
        BackpackInventory.insert(inventory, single);
        check(single.stackSize == 4 && legalStacks(inventory), "unstackables never become oversized stacks");

        inventory = new InventoryBasic("filtered", true, 41) {

            @Override
            public boolean isItemValidForSlot(int slot, ItemStack item) {
                return slot != 0;
            }
        };
        check(BackpackInventory.capacity(inventory, target) == 35 * 64, "rejected slots excluded from capacity");
        extracted = tagged(2304, "Alice");
        BackpackInventory.insert(inventory, extracted);
        check(
            extracted.stackSize == 64 && inventory.getStackInSlot(0) == null,
            "slot rejection respected on insertion");

        inventory = inventory();
        check(BackpackInventory.capacity(inventory, target) > 0, "capacity before a storage hook changes slots");
        for (int slot = 0; slot < 36; slot++) inventory.setInventorySlotContents(slot, new ItemStack(OTHER, 64));
        extracted = tagged(1000, "Alice");
        BackpackInventory.insert(inventory, extracted);
        check(
            extracted.stackSize == 1000 && count(inventory, target) == 0,
            "insertion rechecks slots and retains rejected items");

        Random random = new Random(20261112L);
        for (int run = 0; run < 300; run++) {
            inventory = inventory();
            ItemStack[] protectedSlots = new ItemStack[41];
            for (int slot = 0; slot < 41; slot++) {
                int kind = random.nextInt(4);
                ItemStack item = kind == 0 ? null
                    : kind == 1 ? tagged(random.nextInt(64) + 1, "Alice")
                        : kind == 2 ? tagged(random.nextInt(64) + 1, "Bob")
                            : new ItemStack(OTHER, random.nextInt(64) + 1);
                inventory.setInventorySlotContents(slot, item);
                if (slot >= 36 || !matches(item, target)) protectedSlots[slot] = item;
            }
            int before = count(inventory, target);
            int supplied = random.nextInt(4000) + 1;
            extracted = tagged(supplied, "Alice");
            BackpackInventory.insert(inventory, extracted);
            if (before + supplied != count(inventory, target) + extracted.stackSize || !legalStacks(inventory))
                throw new AssertionError("item conservation or legal stack sizes failed in trial " + run);
            for (int slot = 0; slot < 41; slot++) {
                ItemStack original = protectedSlots[slot];
                if ((slot >= 36 || original != null) && inventory.getStackInSlot(slot) != original)
                    throw new AssertionError("protected item changed in trial " + run);
            }
        }
        check(true, "300 seeded mixed inventories conserve extraction, other items, NBT, and armor slots");
        System.out.println("PASS: " + checks + " backpack fill regression checks (including 300 conservation trials)");
    }
}
