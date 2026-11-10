package com.ae2conveniencefeatures;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public final class Config {

    public static boolean neiActions = true;
    public static boolean inventoryOverlay = true;
    public static boolean neiMiddleCraft = true;
    public static boolean worldPickBlock = true;
    public static boolean completedCraftRows = true;
    public static int searchKeyCode = 33;

    private Config() {}

    public static void load(File file) {
        Configuration config = new Configuration(file);
        neiActions = config.getBoolean("neiActions", "features", true, "Shift-click NEI items to retrieve or craft.");
        inventoryOverlay = config.getBoolean("inventoryOverlay", "features", true, "Show AE stock on NEI panels.");
        neiMiddleCraft = config.getBoolean(
            "neiMiddleCraft",
            "features",
            true,
            "Middle-click NEI items to open crafting even when stocked.");
        worldPickBlock = config
            .getBoolean("worldPickBlock", "features", true, "Pick blocks from AE stock or open crafting.");
        completedCraftRows = config.getBoolean(
            "completedCraftRows",
            "features",
            true,
            "Temporarily show completed player crafting orders first in ME terminals (60 seconds or until closed).");
        searchKeyCode = config
            .getInt("searchKeyCode", "client", 33, 0, 255, "LWJGL key code: F=33, 0 disables hover-to-search.");
        if (config.hasChanged()) config.save();
    }
}
