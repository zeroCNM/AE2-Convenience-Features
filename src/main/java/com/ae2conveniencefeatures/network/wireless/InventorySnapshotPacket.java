package com.ae2conveniencefeatures.network.wireless;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

import com.ae2conveniencefeatures.AE2ConvenienceFeatures;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class InventorySnapshotPacket implements IMessage {

    public long requestId;
    public long gridId;
    public long revision;
    public int dimension;
    public int terminalSlot;
    public boolean validTerminal;
    public boolean accessible;
    public List<ItemStack> items = new ArrayList<ItemStack>();
    public List<Long> counts = new ArrayList<Long>();
    public List<Boolean> craftable = new ArrayList<Boolean>();

    @Override
    public void fromBytes(ByteBuf buf) {
        requestId = buf.readLong();
        gridId = buf.readLong();
        revision = buf.readLong();
        dimension = buf.readInt();
        terminalSlot = buf.readInt();
        validTerminal = buf.readBoolean();
        accessible = buf.readBoolean();
        int size = buf.readUnsignedShort();
        if (size > InventoryQueryPacket.MAX_ITEMS) throw new IllegalArgumentException("Oversized wireless snapshot");
        for (int i = 0; i < size; i++) {
            ItemStack item = FullStackCodec.read(buf);
            if (item == null) throw new IllegalArgumentException("Invalid snapshot item");
            items.add(item);
            counts.add(Math.max(0, buf.readLong()));
            craftable.add(buf.readBoolean());
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(requestId);
        buf.writeLong(gridId);
        buf.writeLong(revision);
        buf.writeInt(dimension);
        buf.writeInt(terminalSlot);
        buf.writeBoolean(validTerminal);
        buf.writeBoolean(accessible);
        buf.writeShort(items.size());
        for (int i = 0; i < items.size(); i++) {
            FullStackCodec.write(buf, items.get(i));
            buf.writeLong(counts.get(i));
            buf.writeBoolean(craftable.get(i));
        }
    }

    public static final class Handler implements IMessageHandler<InventorySnapshotPacket, IMessage> {

        @Override
        public IMessage onMessage(InventorySnapshotPacket message, MessageContext context) {
            AE2ConvenienceFeatures.proxy.receiveWirelessInventory(message);
            return null;
        }
    }
}
