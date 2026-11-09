package com.ae2conveniencefeatures;

import net.minecraft.client.Minecraft;

import com.ae2conveniencefeatures.client.wireless.WirelessClient;
import com.ae2conveniencefeatures.network.wireless.InventorySnapshotPacket;
import com.ae2conveniencefeatures.network.wireless.WorldPickResultPacket;

public final class ClientProxy extends CommonProxy {

    @Override
    public void postInit() {
        WirelessClient.register();
    }

    @Override
    public void receiveWirelessInventory(InventorySnapshotPacket packet) {
        Minecraft.getMinecraft()
            .func_152344_a(() -> WirelessClient.receive(packet));
    }

    @Override
    public void invalidateWirelessInventory(long gridId, long revision) {
        Minecraft.getMinecraft()
            .func_152344_a(() -> WirelessClient.invalidate(gridId, revision));
    }

    @Override
    public void receiveWorldPickResult(WorldPickResultPacket packet) {
        Minecraft.getMinecraft()
            .func_152344_a(() -> WirelessClient.receivePickResult(packet));
    }
}
