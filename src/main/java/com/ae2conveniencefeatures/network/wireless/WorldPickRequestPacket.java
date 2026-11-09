package com.ae2conveniencefeatures.network.wireless;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.ae2conveniencefeatures.network.ModNetwork;
import com.ae2conveniencefeatures.wireless.ServerTasks;
import com.ae2conveniencefeatures.wireless.WirelessService;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class WorldPickRequestPacket implements IMessage {

    public long requestId;
    public int dimension;
    public int x, y, z, side, selectedSlot;
    public ItemStack item;

    @Override
    public void fromBytes(ByteBuf buf) {
        requestId = buf.readLong();
        dimension = buf.readInt();
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
        side = buf.readUnsignedByte();
        selectedSlot = buf.readUnsignedByte();
        if (requestId <= 0 || side > 5 || selectedSlot > 8 || y < 0 || y > 255) {
            throw new IllegalArgumentException("Invalid world pick request");
        }
        item = FullStackCodec.read(buf);
        item.stackSize = 1;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(requestId);
        buf.writeInt(dimension);
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        buf.writeByte(side);
        buf.writeByte(selectedSlot);
        FullStackCodec.write(buf, item);
    }

    public static final class Handler implements IMessageHandler<WorldPickRequestPacket, IMessage> {

        @Override
        public IMessage onMessage(WorldPickRequestPacket message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            if (!ServerTasks.submit(player, () -> WirelessService.pick(player, message))) {
                ModNetwork.CHANNEL
                    .sendTo(new WorldPickResultPacket(message.requestId, message.dimension, false), player);
            }
            return null;
        }
    }
}
