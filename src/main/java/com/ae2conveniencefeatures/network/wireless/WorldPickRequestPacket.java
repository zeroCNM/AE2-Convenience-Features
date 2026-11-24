package com.ae2conveniencefeatures.network.wireless;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.ae2conveniencefeatures.network.ModNetwork;
import com.ae2conveniencefeatures.wireless.ServerTasks;
import com.ae2conveniencefeatures.wireless.WirelessService;
import com.ae2conveniencefeatures.wireless.WirelessTarget;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class WorldPickRequestPacket implements IMessage {

    public long requestId;
    public int dimension;
    public int x, y, z, side, selectedSlot;
    public ItemStack item;
    private byte[] encoded;

    public static boolean isVanillaSlot(int slot) {
        return slot >= 0 && slot < 9;
    }

    public boolean prepare() {
        if (!isVanillaSlot(selectedSlot) || item == null || item.getItem() == null) return false;
        encoded = WirelessTargetCodec.encode(WirelessTarget.item(item), WirelessTargetCodec.CLIENT_PACKET_BUDGET - 27);
        return encoded != null;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        requestId = buf.readLong();
        dimension = buf.readInt();
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
        side = buf.readUnsignedByte();
        selectedSlot = buf.readUnsignedByte();
        if (requestId <= 0 || side > 5 || y < 0 || y > 255) {
            throw new IllegalArgumentException("Invalid world pick request");
        }
        if (isVanillaSlot(selectedSlot)) item = FullStackCodec.read(buf);
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
        buf.writeBytes(encoded);
    }

    public static final class Handler implements IMessageHandler<WorldPickRequestPacket, IMessage> {

        @Override
        public IMessage onMessage(WorldPickRequestPacket message, MessageContext context) {
            if (!isVanillaSlot(message.selectedSlot)) return null;
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            if (!ServerTasks.submit(player, () -> WirelessService.pick(player, message))) {
                ModNetwork.CHANNEL
                    .sendTo(new WorldPickResultPacket(message.requestId, message.dimension, false), player);
            }
            return null;
        }
    }
}
