package com.ae2conveniencefeatures.network.wireless;

import com.ae2conveniencefeatures.AE2ConvenienceFeatures;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class WorldPickResultPacket implements IMessage {

    public long requestId;
    public int dimension;
    public boolean vanillaFallback;

    public WorldPickResultPacket() {}

    public WorldPickResultPacket(long requestId, int dimension, boolean vanillaFallback) {
        this.requestId = requestId;
        this.dimension = dimension;
        this.vanillaFallback = vanillaFallback;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        requestId = buf.readLong();
        dimension = buf.readInt();
        vanillaFallback = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(requestId);
        buf.writeInt(dimension);
        buf.writeBoolean(vanillaFallback);
    }

    public static final class Handler implements IMessageHandler<WorldPickResultPacket, IMessage> {

        @Override
        public IMessage onMessage(WorldPickResultPacket message, MessageContext context) {
            AE2ConvenienceFeatures.proxy.receiveWorldPickResult(message);
            return null;
        }
    }
}
