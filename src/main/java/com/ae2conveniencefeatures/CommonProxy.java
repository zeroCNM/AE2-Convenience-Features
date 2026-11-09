package com.ae2conveniencefeatures;

import com.ae2conveniencefeatures.network.wireless.InventorySnapshotPacket;
import com.ae2conveniencefeatures.network.wireless.WorldPickResultPacket;
import com.ae2conveniencefeatures.wireless.ServerTasks;

import cpw.mods.fml.common.FMLCommonHandler;

public class CommonProxy {

    public void preInit() {
        FMLCommonHandler.instance()
            .bus()
            .register(new ServerTasks());
    }

    public void postInit() {}

    public void receiveWirelessInventory(InventorySnapshotPacket packet) {}

    public void invalidateWirelessInventory(long gridId, long revision) {}

    public void receiveWorldPickResult(WorldPickResultPacket packet) {}
}
