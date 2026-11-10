package com.ae2conveniencefeatures.network;

import com.ae2conveniencefeatures.network.crafting.CompletedCraftsPacket;
import com.ae2conveniencefeatures.network.wireless.InventoryInvalidationPacket;
import com.ae2conveniencefeatures.network.wireless.InventoryQueryPacket;
import com.ae2conveniencefeatures.network.wireless.InventorySnapshotPacket;
import com.ae2conveniencefeatures.network.wireless.WirelessActionPacket;
import com.ae2conveniencefeatures.network.wireless.WorldPickRequestPacket;
import com.ae2conveniencefeatures.network.wireless.WorldPickResultPacket;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

public final class ModNetwork {

    public static final String CHANNEL_ID = "ae2cf";
    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL_ID);

    private ModNetwork() {}

    public static void registerPackets() {
        CHANNEL.registerMessage(InventoryQueryPacket.Handler.class, InventoryQueryPacket.class, 0, Side.SERVER);
        CHANNEL.registerMessage(InventorySnapshotPacket.Handler.class, InventorySnapshotPacket.class, 1, Side.CLIENT);
        CHANNEL.registerMessage(WirelessActionPacket.Handler.class, WirelessActionPacket.class, 2, Side.SERVER);
        CHANNEL.registerMessage(
            InventoryInvalidationPacket.Handler.class,
            InventoryInvalidationPacket.class,
            3,
            Side.CLIENT);
        CHANNEL.registerMessage(WorldPickRequestPacket.Handler.class, WorldPickRequestPacket.class, 4, Side.SERVER);
        CHANNEL.registerMessage(WorldPickResultPacket.Handler.class, WorldPickResultPacket.class, 5, Side.CLIENT);
        CHANNEL.registerMessage(CompletedCraftsPacket.Handler.class, CompletedCraftsPacket.class, 6, Side.CLIENT);
    }
}
