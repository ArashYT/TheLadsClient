package com.thelads.core.config;

import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import static com.thelads.core.config.IntegratedSettings.*;

/** Public configuration adapters for the pinned 26.2 reference engines. */
public final class NewEngineIntegrations {
    private NewEngineIntegrations() {}

    public static Page open(String id) throws Exception {
        if ("modern-advancements".equals(id)) {
            Object config = get(Class.forName("net.a5ho999.modernadvancements.ModernAdvancementsClient"), "CONFIG");
            return modern(config, () -> call(config, "save"), gameFile("config/modern-advancements/modern-advancements_client.json"));
        }
        if ("resourcify".equals(id)) {
            Object companion = get(Class.forName("dev.dediamondpro.resourcify.config.Config"), "Companion");
            Object config = call(companion, "getInstance");
            Method save = companion.getClass().getMethod("save", config.getClass());
            return resourcify(config, () -> save.invoke(companion, config), gameFile("config/resourcify.json"));
        }
        return null;
    }

    static Page modern(Object config, Save save, Path file) throws Exception {
        Map<String, Method> persisted = new LinkedHashMap<>();
        Builder builder = new Builder("Modern Advancements", () -> saveAndVerify(config, save, file, persisted));
        for (String name : new String[] {"blurDescription", "blurRequirements", "removeCompletedTracking",
                "takeScreenshots", "matchWindowSize", "boundingBoxAnchor", "trackerSize", "trackerDisplayMode",
                "toastSize", "toastAnimation", "toastFrameStyle", "toastBackground", "toastIconLayout",
                "toastAnchor", "layoutMode", "toastDisplayStyle", "toastSoundTask", "toastSoundChallenge"})
            property(builder, config, persisted, name, name, name);
        property(builder, config, persisted, "hideTracker", "hideTracker", "hideTracking");
        property(builder, config, persisted, "trackPathways", "trackPathways", "autoTrackPathway");
        return builder.help("Advancement, tracker and toast preferences save to the engine's live configuration. Advanced opens its layout editors. Open advancements in a world to search and track goals.").build();
    }

    static Page resourcify(Object config, Save save, Path file) throws Exception {
        Map<String, Method> persisted = new LinkedHashMap<>();
        Builder builder = new Builder("Resourcify", () -> saveAndVerify(config, save, file, persisted));
        for (String name : new String[] {"FullResThumbnail", "OpenLinkInResourcify", "AdsEnabled",
                "ResourcePacksEnabled", "DataPacksEnabled", "ShaderPacksEnabled", "WorldsEnabled", "GifsDisabled"})
            property(builder, config, persisted, "get" + name, "set" + name,
                Character.toLowerCase(name.charAt(0)) + name.substring(1));
        return builder.help("Browse packs from Minecraft's Resource Packs screen with its + button. Advanced opens browser preferences. Downloads happen only when requested in the browser; existing providers and GUI scale are preserved.").build();
    }

    private static void property(Builder builder, Object config, Map<String, Method> persisted,
            String getterName, String setterName, String jsonName) throws Exception {
        Method getter = config.getClass().getMethod(getterName);
        Method setter = config.getClass().getMethod(setterName, getter.getReturnType());
        builder.property(label(jsonName), getter.getReturnType(), () -> getter.invoke(config), value -> setter.invoke(config, value));
        persisted.put(jsonName, getter);
    }

    static void saveAndVerify(Object config, Save save, Path file, Map<String, Method> properties) throws Exception {
        save.run();
        // Both upstream saves catch I/O failures. Read the engine-owned file before claiming success.
        var persisted = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        for (var property : properties.entrySet()) {
            Object value = property.getValue().invoke(config);
            JsonPrimitive expected = value instanceof Boolean bool ? new JsonPrimitive(bool)
                : new JsonPrimitive(((Enum<?>)value).name());
            var actual = persisted.get(property.getKey());
            if (!expected.equals(actual))
                throw new IOException("The engine did not save " + label(property.getKey()) + ". Check the game config directory is writable.");
        }
    }

    private static Path gameFile(String relative) throws Exception {
        Class<?> loader = Class.forName("net.fabricmc.loader.api.FabricLoader");
        Path game = (Path)loader.getMethod("getGameDir").invoke(call(loader, "getInstance"));
        return game.resolve(relative);
    }
}
