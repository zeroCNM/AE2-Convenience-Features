package com.ae2conveniencefeatures.network.wireless;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.entity.player.EntityPlayerMP;

import com.ae2conveniencefeatures.wireless.ServerTasks;
import com.ae2conveniencefeatures.wireless.WirelessService;
import com.ae2conveniencefeatures.wireless.WirelessTarget;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class InventoryQueryPacket implements IMessage {

    public static final int MAX_ITEMS = 512;
    private static final int TARGET_BUDGET = WirelessTargetCodec.CLIENT_PACKET_BUDGET - 16;
    public long requestId;
    public int dimension;
    public boolean extractableOnly;
    public final List<WirelessTarget> targets = new ArrayList<>();
    private final List<byte[]> encoded = new ArrayList<>();

    public InventoryQueryPacket() {}

    public static InventoryQueryPacket pack(int dimension, Iterable<WirelessTarget> candidates,
        Consumer<WirelessTarget> skipped) {
        InventoryQueryPacket packet = new InventoryQueryPacket();
        packet.dimension = dimension;
        int remaining = TARGET_BUDGET;
        for (WirelessTarget candidate : candidates) {
            if (packet.targets.size() == MAX_ITEMS) break;
            byte[] bytes = WirelessTargetCodec.encode(candidate, TARGET_BUDGET);
            if (bytes == null) {
                skipped.accept(candidate);
                continue;
            }
            if (bytes.length > remaining) break; // Oldest or unqueried targets go first next time.
            packet.encoded.add(bytes);
            packet.targets.add(candidate);
            remaining -= bytes.length;
        }
        return packet;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        requestId = buf.readLong();
        dimension = buf.readInt();
        extractableOnly = buf.readBoolean();
        int size = buf.readUnsignedShort();
        if (size > MAX_ITEMS) throw new IllegalArgumentException("Oversized wireless query");
        targets.clear();
        encoded.clear();
        for (int i = 0; i < size; i++) {
            WirelessTarget target = WirelessTargetCodec.read(buf);
            if (target != null) targets.add(target);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(requestId);
        buf.writeInt(dimension);
        buf.writeBoolean(extractableOnly);
        buf.writeShort(encoded.size());
        for (byte[] entry : encoded) buf.writeBytes(entry);
    }

    public static final class Handler implements IMessageHandler<InventoryQueryPacket, IMessage> {

        @Override
        public IMessage onMessage(InventoryQueryPacket message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            ServerTasks.submit(player, () -> WirelessService.query(player, message));
            return null;
        }
    }
}
