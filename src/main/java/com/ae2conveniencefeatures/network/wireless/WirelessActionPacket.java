package com.ae2conveniencefeatures.network.wireless;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.ae2conveniencefeatures.wireless.ServerTasks;
import com.ae2conveniencefeatures.wireless.WirelessService;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class WirelessActionPacket implements IMessage {

    public static final int TAKE_ONE = 0;
    public static final int TAKE_STACK = 1;
    public static final int CRAFT_ONLY = 2;
    public int action;
    public int dimension;
    public ItemStack item;

    public WirelessActionPacket() {}

    public WirelessActionPacket(int action, int dimension, ItemStack item) {
        this.action = action;
        this.dimension = dimension;
        this.item = item == null ? null : item.copy();
        if (this.item != null) this.item.stackSize = 1;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        action = buf.readUnsignedByte();
        if (action > CRAFT_ONLY) throw new IllegalArgumentException("Invalid wireless action");
        dimension = buf.readInt();
        item = FullStackCodec.read(buf);
        item.stackSize = 1;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(action);
        buf.writeInt(dimension);
        FullStackCodec.write(buf, item);
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
