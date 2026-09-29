package com.thelads.core;

import com.thelads.core.mods.LoadedMod;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** A 26.3 profile as the launcher snapshot (dependencies as "id predicate") and Fabric Loader would describe it. */
final class ModsFixture {
    static final String INVENTORY = """
        {"schema": 1, "minecraftVersion": "26.3", "generatedAt": "2026-09-29T12:00:00Z", "entries": [
          {"id": "theladscore", "displayName": "The Lads Core", "version": "1.2.0", "ownership": "core", "status": "installed",
           "fileName": "theladscore.jar", "filePath": "C:/game/mods/theladscore.jar", "enabledOnDisk": true, "requestedEnabled": true, "canToggle": true, "depends": ["minecraft 26.3", "fabric-api *"],
           "children": [
             {"id": "catconfig-mc", "displayName": "CatConfig MC", "version": "0.2.1", "ownership": "embedded", "status": "embedded",
              "canToggle": false, "toggleBlockedReason": "Embedded inside The Lads Core; disable The Lads Core to remove it",
              "isLibrary": true, "parentId": "theladscore",
              "children": [{"id": "io_github_lgatodu47_catconfig", "displayName": "CatConfig", "ownership": "embedded",
                            "status": "embedded", "canToggle": false, "isLibrary": true}]}]},
          {"id": "fabric-api", "displayName": "Fabric API", "upstreamName": "Fabric API", "version": "0.161.0+26.3", "ownership": "pack",
           "status": "installed", "filePath": "C:/game/mods/fabric-api.jar", "enabledOnDisk": true, "requestedEnabled": true, "canToggle": true, "projectId": "P7dR8mSH",
           "isLibrary": true, "provides": ["fabric"], "depends": ["minecraft"],
           "children": [{"id": "fabric-api-base", "displayName": "Fabric API Base", "ownership": "embedded", "status": "embedded",
                         "canToggle": false, "isLibrary": true}]},
          {"id": "sodium", "displayName": "sodium", "upstreamName": "Sodium", "version": "0.7.0", "ownership": "pack",
           "status": "installed", "filePath": "C:/game/mods/sodium.jar", "enabledOnDisk": true, "requestedEnabled": true, "canToggle": true, "projectId": "AANobbMI",
           "depends": ["minecraft"]},
          {"id": "lambdynlights", "displayName": "LambDynamicLights", "upstreamName": "LambDynamicLights", "version": "4.0",
           "ownership": "pack", "status": "installed", "filePath": "C:/game/mods/lambdynlights.jar", "enabledOnDisk": true,
           "requestedEnabled": true, "canToggle": true, "depends": ["fabric-api >=0.100.0"]},
          {"id": "myjar", "displayName": "My Jar", "version": "1.0", "ownership": "user", "status": "disabled",
           "filePath": "C:/game/mods/myjar.jar.disabled", "enabledOnDisk": false,
           "requestedEnabled": false, "canToggle": true, "depends": ["fabric-api *"]},
          {"id": "iris", "upstreamName": "Iris Shaders", "version": "1.9", "ownership": "pack", "status": "pendingDownload", "fileName": "iris.jar",
           "requestedEnabled": true, "canToggle": true, "depends": [], "dependenciesKnown": true},
          {"id": "zoomify", "upstreamName": "Zoomify", "ownership": "pack", "status": "notDownloaded",
           "requestedEnabled": false, "canToggle": true},
          {"id": "appleskin", "upstreamName": "AppleSkin", "ownership": "pack", "status": "unavailable", "requestedEnabled": false,
           "canToggle": false, "toggleBlockedReason": "Not in the Lads pack for 26.3",
           "note": "Not in the Lads pack for 26.3 (included for 1.21.11)."},
          {"id": "minecraft", "displayName": "Minecraft", "version": "26.3", "ownership": "platform", "status": "installed",
           "requestedEnabled": true, "canToggle": false, "toggleBlockedReason": "Platform component"},
          {"id": "FPS", "displayName": "FPS (stale launcher copy)", "ownership": "nativeModule", "status": "installed", "canToggle": true}
        ]}""";

    static final List<LoadedMod> LOADED = List.of(
        mod("theladscore", "The Lads Core", null, "fabric", false, "minecraft", "fabric-api"),
        mod("catconfig-mc", "CatConfig MC", "theladscore", "fabric", true),
        mod("io_github_lgatodu47_catconfig", "CatConfig", "catconfig-mc", "fabric", false),
        mod("fabric-api", "Fabric API", null, "fabric", true, "minecraft"),
        mod("fabric-api-base", "Fabric API Base", "fabric-api", "fabric", false),
        mod("fabric-networking-api-v1", "Fabric Networking API (v1)", "fabric-api", "fabric", false),
        mod("sodium", "Sodium", null, "fabric", false, "minecraft"),
        mod("lambdynlights", "LambDynamicLights", null, "fabric", false, "fabric-api"),
        mod("lithium", "Lithium", null, "fabric", false, "minecraft"),
        mod("minecraft", "Minecraft", null, "builtin", false),
        mod("java", "OpenJDK 64-Bit Server VM", null, "builtin", false),
        mod("fabricloader", "Fabric Loader", null, "builtin", false));

    private ModsFixture() {}

    static LoadedMod mod(String id, String name, String parent, String kind, boolean library, String... depends) {
        return new LoadedMod(id, name, "1.0", parent, List.of(), "MIT", List.of(depends), List.of(), library, kind);
    }

    static void writeInventory(Path gameDirectory) throws IOException {
        Path file = gameDirectory.resolve(".lads-mod-cache").resolve("inventory.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, INVENTORY);
    }
}
