package com.ae2conveniencefeatures.wireless;

import java.lang.reflect.Method;
import java.util.Collections;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.ae2conveniencefeatures.crafting.RecentCraftHistory;
import com.ae2conveniencefeatures.network.crafting.CompletedCraftsPacket;
import com.ae2conveniencefeatures.network.wireless.FullStackCodec;
import com.ae2conveniencefeatures.network.wireless.InventoryQueryPacket;
import com.ae2conveniencefeatures.network.wireless.InventorySnapshotPacket;
import com.ae2conveniencefeatures.network.wireless.WirelessActionPacket;
import com.ae2conveniencefeatures.network.wireless.WorldPickRequestPacket;
import com.ae2conveniencefeatures.network.wireless.WorldPickResultPacket;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/** Real Forge/Netty codec checks, including NBT on a deliberately non-sharing item. No game is started. */
public final class PacketCodecTest {

    private static int checks;

    private static void check(boolean condition, String name) {
        checks++;
        if (!condition) throw new AssertionError(name);
    }

    private static <T extends IMessage> T roundTrip(IMessage source, T target) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            source.toBytes(buffer);
            target.fromBytes(buffer);
            check(!buffer.isReadable(), "packet consumed completely");
            return target;
        } finally {
            buffer.release();
        }
    }

    private static void rejects(IMessage target, ByteBuf bytes, String name) {
        try {
            try {
                target.fromBytes(bytes);
                throw new AssertionError(name);
            } catch (IllegalArgumentException expected) {
                check(true, name);
            }
        } finally {
            bytes.release();
        }
    }

    private static ByteBuf worldHeader(long id, int y, int side, int slot) {
        return Unpooled.buffer()
            .writeLong(id)
            .writeInt(0)
            .writeInt(0)
            .writeInt(y)
            .writeInt(0)
            .writeByte(side)
            .writeByte(slot);
    }

    public static void main(String[] args) throws Exception {
        Item item = new Item() {

            @Override
            public boolean getShareTag() {
                return false;
            }
        };
        // This isolated test JVM has no FML mod lifecycle. Seed only its registry mapping via
        // the verified Forge method, avoiding Loader initialization; production uses no such access.
        Method seed = Item.itemRegistry.getClass()
            .getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
        seed.setAccessible(true);
        seed.invoke(Item.itemRegistry, 30000, "ae2cf_regression:private_nbt", item);
        ItemStack stack = new ItemStack(item, 64, 7);
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagCompound nested = new NBTTagCompound();
        nested.setString("owner", "Alice");
        tag.setTag("nested", nested);
        tag.setIntArray("configuration", new int[] { 1, 3, 5 });
        stack.setTagCompound(tag);

        ByteBuf bytes = Unpooled.buffer();
        try {
            ByteBufUtils.writeItemStack(bytes, stack);
            check(
                !ByteBufUtils.readItemStack(bytes)
                    .hasTagCompound(),
                "vanilla codec omits private NBT");
            bytes.clear();
            FullStackCodec.write(bytes, stack);
            ItemStack decoded = FullStackCodec.read(bytes);
            check(new StackKey(stack).equals(new StackKey(decoded)), "complete metadata and deep NBT round trip");
            check(decoded.stackSize == 1 && stack.stackSize == 64, "wire count normalized without mutating source");
        } finally {
            bytes.release();
        }

        InventoryQueryPacket query = roundTrip(
            new InventoryQueryPacket(123456789L, -1, Collections.singletonList(stack), true),
            new InventoryQueryPacket());
        check(
            query.requestId == 123456789L && query.dimension == -1 && query.extractableOnly,
            "query context preserved");
        check(new StackKey(query.items.get(0)).equals(new StackKey(stack)), "query preserves private NBT");

        InventorySnapshotPacket snapshot = new InventorySnapshotPacket();
        snapshot.requestId = 5;
        snapshot.gridId = 9;
        snapshot.revision = 27;
        snapshot.dimension = -1;
        snapshot.terminalSlot = 100013;
        snapshot.validTerminal = true;
        snapshot.accessible = true;
        snapshot.items.add(stack);
        snapshot.counts.add(3_400_000_000_000L);
        snapshot.craftable.add(true);
        InventorySnapshotPacket decoded = roundTrip(snapshot, new InventorySnapshotPacket());
        check(
            decoded.gridId == 9 && decoded.revision == 27 && decoded.terminalSlot == 100013,
            "snapshot grid, revision and encoded slot preserved");
        check(decoded.counts.get(0) == 3_400_000_000_000L && decoded.craftable.get(0), "long stock count preserved");
        check(
            decoded.validTerminal && decoded.accessible
                && new StackKey(decoded.items.get(0)).equals(new StackKey(stack)),
            "snapshot flags and complete NBT preserved");

        WirelessActionPacket take = roundTrip(
            new WirelessActionPacket(WirelessActionPacket.TAKE_STACK, -1, stack),
            new WirelessActionPacket());
        check(
            take.action == WirelessActionPacket.TAKE_STACK && take.item.stackSize == 1
                && new StackKey(take.item).equals(new StackKey(stack)),
            "take action preserves identity, not client quantity");
        WirelessActionPacket craft = roundTrip(
            new WirelessActionPacket(WirelessActionPacket.CRAFT_ONLY, -1, stack),
            new WirelessActionPacket());
        check(
            craft.action == WirelessActionPacket.CRAFT_ONLY && craft.item.stackSize == 1
                && new StackKey(craft.item).equals(new StackKey(stack)),
            "explicit crafting preserves full identity independently of item quantity");
        WorldPickRequestPacket world = new WorldPickRequestPacket();
        world.requestId = 42;
        world.dimension = 2;
        world.selectedSlot = 7;
        world.item = stack;
        world.x = -100;
        world.y = 255;
        world.z = 300;
        world.side = 5;
        world = roundTrip(world, new WorldPickRequestPacket());
        check(
            world.requestId == 42 && world.selectedSlot == 7
                && world.dimension == 2
                && world.x == -100
                && world.y == 255
                && world.z == 300
                && world.side == 5,
            "world request preserves checked context and coordinates");
        check(
            world.item.stackSize == 1 && new StackKey(world.item).equals(new StackKey(stack)),
            "world pick carries complete metadata and NBT");
        for (boolean fallback : new boolean[] { false, true }) {
            WorldPickResultPacket result = roundTrip(
                new WorldPickResultPacket(42, 2, fallback),
                new WorldPickResultPacket());
            check(
                result.requestId == 42 && result.dimension == 2 && result.vanillaFallback == fallback,
                "world result preserves replay decision");
        }

        CompletedCraftsPacket completed = new CompletedCraftsPacket();
        completed.sequence = 9876543210L;
        completed.gridId = 2;
        completed.windowId = 93;
        completed.dimension = -29;
        completed.items.add(stack);
        CompletedCraftsPacket completedCopy = roundTrip(completed, new CompletedCraftsPacket());
        check(
            completedCopy.sequence == completed.sequence && completedCopy.gridId == 2
                && completedCopy.windowId == 93
                && completedCopy.dimension == -29,
            "completed orders retain terminal/grid/sequence scope");
        check(
            new StackKey(completedCopy.items.get(0)).equals(new StackKey(stack))
                && completedCopy.items.get(0).stackSize == 1,
            "completed item identity retains full NBT without claiming quantity");
        completed.items.clear();
        check(roundTrip(completed, new CompletedCraftsPacket()).items.isEmpty(), "empty terminal binding is valid");
        rejects(
            new CompletedCraftsPacket(),
            Unpooled.buffer()
                .writeLong(1)
                .writeLong(1)
                .writeInt(0)
                .writeInt(1)
                .writeShort(RecentCraftHistory.MAX_ITEMS + 1),
            "oversized completed batch rejected before allocation");
        rejects(
            new CompletedCraftsPacket(),
            Unpooled.buffer()
                .writeLong(1)
                .writeLong(0)
                .writeInt(0)
                .writeInt(1)
                .writeShort(0),
            "missing completed-order network identity rejected");

        rejects(
            new InventoryQueryPacket(),
            Unpooled.buffer()
                .writeLong(1)
                .writeInt(0)
                .writeBoolean(false)
                .writeShort(InventoryQueryPacket.MAX_ITEMS + 1),
            "oversized query rejected before allocating items");
        rejects(
            new InventorySnapshotPacket(),
            Unpooled.buffer()
                .writeLong(1)
                .writeLong(1)
                .writeLong(1)
                .writeInt(0)
                .writeInt(0)
                .writeBoolean(true)
                .writeBoolean(true)
                .writeShort(InventoryQueryPacket.MAX_ITEMS + 1),
            "oversized snapshot rejected");
        rejects(
            new WirelessActionPacket(),
            Unpooled.buffer()
                .writeByte(3),
            "unknown action rejected");
        rejects(new WorldPickRequestPacket(), worldHeader(1, 0, 6, 0), "invalid side rejected");
        rejects(new WorldPickRequestPacket(), worldHeader(1, 0, 0, 9), "invalid selected slot rejected");
        rejects(new WorldPickRequestPacket(), worldHeader(1, -1, 0, 0), "invalid height rejected");
        rejects(new WorldPickRequestPacket(), worldHeader(0, 0, 0, 0), "invalid request id rejected");
        System.out.println("PASS: " + checks + " Forge/Netty packet regression checks");
    }
}
