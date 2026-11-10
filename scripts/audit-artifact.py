#!/usr/bin/env python3
"""Audit the standalone packaged artifact; this is not a game-client test."""
import hashlib
import json
import struct
import sys
from pathlib import Path
from zipfile import ZipFile

artifact = Path(sys.argv[1])
version = (Path(__file__).resolve().parents[1] / "VERSION").read_text().strip()
prefix = "com/ae2conveniencefeatures/"
assert artifact.name == "AE2 Convenience Features " + version + ".jar", "artifact filename/version mismatch"
with ZipFile(artifact) as archive:
    names = set(archive.namelist())
    assert archive.testzip() is None, "corrupt ZIP entry"
    metadata = json.loads(archive.read("mcmod.info"))["modList"][0]
    assert (metadata["modid"], metadata["name"], metadata["version"]) == (
        "ae2conveniencefeatures", "AE2 Convenience Features", version)
    assert version.encode() in archive.read(prefix + "Tags.class")
    assert version.encode() in archive.read(prefix + "AE2ConvenienceFeatures.class")
    config = json.loads(archive.read("mixins.ae2conveniencefeatures.json"))
    assert config["mixins"] == ["PlatformBaublesMixin", "ae.CraftingCompletionMixin"]
    assert set(config["client"]) == {"nei.ItemsGridInventoryMixin", "nei.HistoryGridAccessor",
        "ae.MonitorSearchAccessor", "ae.InterfaceSearchAccessor", "ae.DiagnosticSearchAccessor",
        "ae.LevelSearchAccessor", "nei.ContainerMiddleClickMixin", "nei.RecipeMiddleClickMixin",
        "nei.RecipeContainerAccessor", "nei.LegacyRecipeInventoryMixin", "nei.RecipeInventoryMixin",
        "ae.RecentItemRepoMixin", "ae.RecentTerminalMixin"}
    for side in ("mixins", "client", "server"):
        for name in config[side]:
            assert config["package"].replace(".", "/") + "/" + name.replace(".", "/") + ".class" in names, name
    assert config["refmap"] in names
    refmap = json.loads(archive.read(config["refmap"]))["mappings"]
    recipe_mapping = refmap[prefix + "mixin/nei/RecipeMiddleClickMixin"]
    assert recipe_mapping["mouseClicked(III)V"].endswith("func_73864_a(III)V")
    assert recipe_mapping["mouseMovedOrUp(III)V"].endswith("func_146286_b(III)V")
    assert recipe_mapping["mouseClickMove(IIIJ)V"].endswith("func_146273_a(IIIJ)V")
    assert recipe_mapping["initGui()V"].endswith("func_73866_w_()V")
    terminal_mapping = refmap[prefix + "mixin/ae/RecentTerminalMixin"]
    assert terminal_mapping["initGui()V"].endswith("func_73866_w_()V")
    assert terminal_mapping["drawScreen(IIF)V"].endswith("func_73863_a(IIF)V")
    manifest = archive.read("META-INF/MANIFEST.MF").decode()
    assert "MixinConfigs: mixins.ae2conveniencefeatures.json" in manifest
    classes = sorted(name for name in names if name.endswith(".class"))
    optional_api_references = []
    for name in classes:
        assert name.startswith(prefix), "bundled dependency: " + name
        assert not name.endswith("Test.class"), "test class packaged"
        data = archive.read(name)
        assert struct.unpack(">H", data[6:8])[0] <= 52, "newer than Java 8: " + name
        for forbidden in (b"com/gali/ae2_auto_pattern_upload", b"NotEnoughEnergistics", b"tenkeitech/"):
            assert forbidden not in data, "obsolete/external dependency: " + name
        if b"com/glodblock/" in data:
            raise AssertionError("hard AE2FC type dependency: " + name)
        if b"baubles/api/" in data:
            optional_api_references.append(name)
        if name.startswith((prefix + "wireless/", prefix + "network/wireless/", prefix + "crafting/",
                            prefix + "network/crafting/")):
            assert b"net/minecraft/client/" not in data, "client type in common class: " + name
    assert optional_api_references == [prefix + "wireless/BaublesBridge.class"]
    service = archive.read(prefix + "wireless/WirelessService.class")
    assert b"getPickBlock" not in service and b"func_149694_d" not in service
    assert b"setPrimaryGui" in service and b"QuickCraftingReturn" in service
    assert prefix + "wireless/QuickCraftingReturn.class" in names
    assert prefix + "network/crafting/CompletedCraftsPacket.class" in names
    for name in ("FullStackCodec", "InventoryQueryPacket", "InventorySnapshotPacket", "WirelessActionPacket",
                 "InventoryInvalidationPacket", "WorldPickRequestPacket", "WorldPickResultPacket"):
        assert prefix + "network/wireless/" + name + ".class" in names
    assert not any("Provider" in name or "Upload" in name for name in names)
print(json.dumps({
    "artifact": artifact.name,
    "version": version,
    "sha256": hashlib.sha256(artifact.read_bytes()).hexdigest(),
    "class_count": len(classes),
    "java8_bytecode": True,
    "version_consistent": True,
    "mixin_resources_present": True,
    "optional_api_isolated": True,
    "no_bundled_dependencies": True,
    "no_old_mod_or_external_mod_hard_dependency": True,
    "no_client_types_in_common_wireless_classes": True,
    "no_client_pick_path_in_server": True,
    "crafting_return_target_present": True,
    "scope": "static packaging checks; runtime checks are recorded separately",
}, ensure_ascii=False, indent=2))
