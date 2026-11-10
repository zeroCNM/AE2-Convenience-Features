package com.ae2conveniencefeatures;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ae2conveniencefeatures.crafting.CompletedCraftsService;
import com.ae2conveniencefeatures.network.ModNetwork;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStoppedEvent;

@Mod(
    modid = AE2ConvenienceFeatures.MODID,
    name = "AE2 Convenience Features",
    version = Tags.VERSION,
    acceptedMinecraftVersions = "[1.7.10]",
    dependencies = "required-after:appliedenergistics2;required-after:NotEnoughItems;after:Baubles;after:Baubles|Expanded")
public final class AE2ConvenienceFeatures {

    public static final String MODID = "ae2conveniencefeatures";
    public static final Logger LOG = LogManager.getLogger(MODID);

    @SidedProxy(
        clientSide = "com.ae2conveniencefeatures.ClientProxy",
        serverSide = "com.ae2conveniencefeatures.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        Config.load(event.getSuggestedConfigurationFile());
        ModNetwork.registerPackets();
        proxy.preInit();
        LOG.info("AE2 Convenience Features {}", Tags.VERSION);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit();
    }

    @Mod.EventHandler
    public void stopped(FMLServerStoppedEvent event) {
        CompletedCraftsService.clear();
    }
}
