package com.ae2conveniencefeatures.network.wireless;

import com.ae2conveniencefeatures.AE2ConvenienceFeatures;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class InventoryInvalidationPacket implements IMessage {

    public long gridId;
    public long revision;

    public InventoryInvalidationPacket() {}

    public InventoryInvalidationPacket(long gridId, long revision) {
        this.gridId = gridId;
        this.revision = revision;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        gridId = buf.readLong();
        revision = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(gridId);
        buf.writeLong(revision);
    }

    public static final class Handler implements IMessageHandler<InventoryInvalidationPacket, IMessage> {

        @Override
        public IMessage onMessage(InventoryInvalidationPacket message, MessageContext context) {
            AE2ConvenienceFeatures.proxy.invalidateWirelessInventory(message.gridId, message.revision);
            return null;
        }
    }
}
