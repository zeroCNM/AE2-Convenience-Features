package com.ae2conveniencefeatures.network.crafting;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

import com.ae2conveniencefeatures.AE2ConvenienceFeatures;
import com.ae2conveniencefeatures.crafting.RecentCraftHistory;
import com.ae2conveniencefeatures.network.wireless.FullStackCodec;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Server-authoritative completion identities, scoped to the currently open terminal and grid. */
public final class CompletedCraftsPacket implements IMessage {

    public long sequence;
    public long gridId;
    public int dimension;
    public int windowId;
    // Oldest first; the client moves each subsequent completion to the front.
    public final List<ItemStack> items = new ArrayList<ItemStack>();

    @Override
    public void fromBytes(ByteBuf buf) {
        sequence = buf.readLong();
        gridId = buf.readLong();
        dimension = buf.readInt();
        windowId = buf.readInt();
        int size = buf.readUnsignedShort();
        if (gridId <= 0 || size > RecentCraftHistory.MAX_ITEMS)
            throw new IllegalArgumentException("Invalid craft rows");
        for (int i = 0; i < size; i++) {
            ItemStack item = FullStackCodec.read(buf);
            if (item == null) throw new IllegalArgumentException("Invalid completed item");
            items.add(item);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(sequence);
        buf.writeLong(gridId);
        buf.writeInt(dimension);
        buf.writeInt(windowId);
        buf.writeShort(items.size());
        for (ItemStack item : items) FullStackCodec.write(buf, item);
    }

    public static final class Handler implements IMessageHandler<CompletedCraftsPacket, IMessage> {

        @Override
        public IMessage onMessage(CompletedCraftsPacket message, MessageContext context) {
            AE2ConvenienceFeatures.proxy.receiveCompletedCrafts(message);
            return null;
        }
    }
}
