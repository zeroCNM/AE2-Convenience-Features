package com.ae2conveniencefeatures.wireless;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Random;

import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** Uses the actual Minecraft inventory; checks ownership and conservation without starting a game. */
public final class HandInventoryTest {

    private static int checks;
    private static final Item TARGET = new Item();
    private static final Item OTHER = new Item();

    private static void check(boolean condition, String name) {
        checks++;
        if (!condition) throw new AssertionError(name);
    }

    private static InventoryBasic inventory() {
        return new InventoryBasic("test", true, 41);
    }

    private static Map<ItemStack, Integer> contents(InventoryBasic inv) {
        Map<ItemStack, Integer> result = new IdentityHashMap<>();
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack != null) {
                if (result.put(stack, stack.stackSize) != null) throw new AssertionError("aliased stack");
            }
        }
        return result;
    }

    public static void main(String[] args) {
        InventoryBasic inv = inventory();
        ItemStack target = new ItemStack(TARGET, 1, 3);
        inv.setInventorySlotContents(7, target);
        inv.setInventorySlotContents(0, target.copy());
        check(HandInventory.selectExisting(inv, 7, target) == 7, "already held item stays selected");
        check(HandInventory.selectExisting(inv, 6, new ItemStack(TARGET, 1, 4)) == -1, "metadata must match");
        ItemStack tagged = target.copy();
        tagged.setTagCompound(new NBTTagCompound());
        tagged.getTagCompound()
            .setString("owner", "Alice");
        check(HandInventory.selectExisting(inv, 6, tagged) == -1, "NBT must match");
        inv = inventory();
        ItemStack held = new ItemStack(OTHER, 17);
        inv.setInventorySlotContents(3, held);
        inv.setInventorySlotContents(20, target);
        int slot = HandInventory.selectExisting(inv, 3, target);
        check(
            slot == 0 && inv.getStackInSlot(0) == target
                && inv.getStackInSlot(20) == null
                && inv.getStackInSlot(3) == held,
            "empty hotbar selected without moving held item");
        inv = inventory();
        for (int i = 0; i < 9; i++) inv.setInventorySlotContents(i, new ItemStack(OTHER, i + 1));
        held = inv.getStackInSlot(4);
        inv.setInventorySlotContents(20, target);
        check(
            HandInventory.selectExisting(inv, 4, target) == 4 && inv.getStackInSlot(4) == target
                && inv.getStackInSlot(20) == held,
            "full hotbar swaps actual owned stacks");
        inv = inventory();
        for (int i = 0; i < 9; i++) inv.setInventorySlotContents(i, new ItemStack(OTHER, i + 1));
        held = inv.getStackInSlot(4);
        ItemStack extracted = new ItemStack(TARGET, 23);
        check(
            HandInventory.place(inv, 4, extracted) == 4 && inv.getStackInSlot(4) == extracted
                && inv.getStackInSlot(9) == held,
            "actual extraction placed and held item preserved in storage");
        inv = inventory();
        for (int i = 0; i < 36; i++) inv.setInventorySlotContents(i, new ItemStack(OTHER, 64));
        Map<ItemStack, Integer> before = contents(inv);
        check(!HandInventory.hasRoom(inv, 4), "armor and extra slots are not usable capacity");
        check(
            HandInventory.place(inv, 4, extracted) == -1 && contents(inv).equals(before) && extracted.stackSize == 23,
            "failed placement leaves inventory and extraction unchanged");
        inv.setInventorySlotContents(38, target);
        check(HandInventory.selectExisting(inv, 4, target) == -1, "extra slots never selected as owned items");
        check(!HandInventory.hasRoom(inv, -1) && !HandInventory.hasRoom(inv, 9), "invalid hotbar selection refused");
        check(HandInventory.place(inventory(), 0, new ItemStack(TARGET, 65)) == -1, "oversized extraction refused");
        check(HandInventory.place(inventory(), 9, extracted) == -1, "invalid destination refused");
        Random random = new Random(20261109L);
        for (int run = 0; run < 200; run++) {
            inv = inventory();
            for (int i = 0; i < 41; i++) {
                if (random.nextBoolean()) inv.setInventorySlotContents(i, new ItemStack(OTHER, random.nextInt(64) + 1));
            }
            before = contents(inv);
            extracted = new ItemStack(TARGET, random.nextInt(64) + 1);
            int count = extracted.stackSize;
            slot = HandInventory.place(inv, random.nextInt(9), extracted);
            Map<ItemStack, Integer> after = contents(inv);
            if (slot >= 0) {
                if (slot > 8 || after.remove(extracted) == null || extracted.stackSize != count)
                    throw new AssertionError("extraction not owned by exactly one hand slot");
            }
            if (!before.equals(after)) throw new AssertionError("lost, copied, or mutated owned stack in trial " + run);
        }
        check(true, "200 seeded inventories preserve every original stack and exactly the actual extraction");
        System.out.println("PASS: " + checks + " hand inventory regression checks (including 200 conservation trials)");
    }
}
