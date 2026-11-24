package com.ae2conveniencefeatures.network.wireless;

import net.minecraft.entity.player.EntityPlayerMP;

import com.ae2conveniencefeatures.wireless.ServerTasks;
import com.ae2conveniencefeatures.wireless.WirelessService;
import com.ae2conveniencefeatures.wireless.WirelessTarget;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class WirelessActionPacket implements IMessage {

    public static final int TAKE_ONE = 0;
    public static final int TAKE_STACK = 1;
    public static final int CRAFT_ONLY = 2;
    public static final int FILL_INVENTORY = 3;
    public int action;
    public int dimension;
    public WirelessTarget target;
    private byte[] encoded;

    public WirelessActionPacket() {}

    public static WirelessActionPacket create(int action, int dimension, WirelessTarget target) {
        byte[] encoded = WirelessTargetCodec.encode(target, WirelessTargetCodec.CLIENT_PACKET_BUDGET - 6);
        if (encoded == null) return null;
        WirelessActionPacket packet = new WirelessActionPacket();
        packet.action = action;
        packet.dimension = dimension;
        packet.target = target;
        packet.encoded = encoded;
        return packet;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        action = buf.readUnsignedByte();
        if (action > FILL_INVENTORY) throw new IllegalArgumentException("Invalid wireless action");
        dimension = buf.readInt();
        target = WirelessTargetCodec.read(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(action);
        buf.writeInt(dimension);
        buf.writeBytes(encoded);
    }

    public static final class Handler implements IMessageHandler<WirelessActionPacket, IMessage> {

        @Override
        public IMessage onMessage(WirelessActionPacket message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            ServerTasks.submit(player, () -> WirelessService.act(player, message));
            return null;
        }
    }
}
