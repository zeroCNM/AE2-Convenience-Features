package com.ae2conveniencefeatures.network.wireless;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.ae2conveniencefeatures.wireless.ServerTasks;
import com.ae2conveniencefeatures.wireless.WirelessService;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class InventoryQueryPacket implements IMessage {

    public static final int MAX_ITEMS = 512;
    public long requestId;
    public int dimension;
    public boolean extractableOnly;
    public List<ItemStack> items = new ArrayList<ItemStack>();

    public InventoryQueryPacket() {}

    public InventoryQueryPacket(long requestId, int dimension, List<ItemStack> items, boolean extractableOnly) {
        this.requestId = requestId;
        this.dimension = dimension;
        this.extractableOnly = extractableOnly;
        for (ItemStack item : items) {
            ItemStack copy = item.copy();
            copy.stackSize = 1;
            this.items.add(copy);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        requestId = buf.readLong();
        dimension = buf.readInt();
        extractableOnly = buf.readBoolean();
        int size = buf.readUnsignedShort();
        if (size > MAX_ITEMS) throw new IllegalArgumentException("Oversized wireless query");
        items.clear();
        for (int i = 0; i < size; i++) {
            ItemStack item = FullStackCodec.read(buf);
            if (item == null || item.getItem() == null) throw new IllegalArgumentException("Invalid query item");
            item.stackSize = 1;
            items.add(item);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(requestId);
        buf.writeInt(dimension);
        buf.writeBoolean(extractableOnly);
        buf.writeShort(items.size());
        for (ItemStack item : items) FullStackCodec.write(buf, item);
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
