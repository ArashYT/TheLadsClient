package com.thelads.core.config;

import java.lang.reflect.*;
import java.util.*;
import org.slf4j.LoggerFactory;
import static com.thelads.core.config.IntegratedSettings.*;

/** Adapters for the pinned pack. Only public configuration APIs are used; engines retain ownership. */
public final class BuiltInIntegrations {
    private static java.util.function.Consumer<String> afterApply = ignored -> {};
    private BuiltInIntegrations() {}
    public static void setAfterApply(java.util.function.Consumer<String> action) { afterApply = action; }
    /** Read-only runtime API check, enabled only by the isolated verification launcher. */
    public static void verifyLoadedAdapters() {
        if (!Boolean.getBoolean("thelads.verifyIntegrations")) return;
        for (String id : new TreeSet<>(ModuleSupport.externalIds())) open(id);
    }
    public static Page open(String id) {
        try {
            Page page = switch (id) {
                case "appleskin" -> appleSkin();
                case "skinlayers3d" -> skinLayers();
                case "notenoughanimations" -> animations();
                case "entityculling" -> entityCulling();
                case "tabtweaks" -> tabTweaks();
                case "capes" -> capes();
                case "betterf3" -> betterF3();
                case "betterstats" -> betterStats();
                case "autoreconnectrf" -> autoReconnect();
                case "modern-advancements", "resourcify" -> NewEngineIntegrations.open(id);
                case "lithium" -> new Builder("Lithium", () -> {}).help("Automatic game-logic optimizations are active. No routine tuning is needed. Advanced shows the loaded engine and version.").build();
                case "ferritecore" -> new Builder("FerriteCore", () -> {}).help("Automatic memory optimizations are active. No routine tuning is needed. Advanced shows the loaded engine and version.").build();
                case "clumps" -> new Builder("Clumps", () -> {}).help("Experience-orb merging runs automatically in your local worlds. Multiplayer servers control their own experience orbs.").build();
                case "scalablelux" -> new Builder("ScalableLux", () -> {}).help("The lighting engine is installed and runs automatically where supported. No in-game switches are required.").build();
                case "immediatelyfast" -> new Builder("ImmediatelyFast", () -> {}).help("Rendering optimizations load at startup. Hardware and mod compatibility are detected by the engine. Advanced shows its installed version.").build();
                default -> {
                    Page advanced = AdvancedIntegrations.open(id);
                    yield advanced != null ? advanced : AdditionalIntegrations.open(id);
                }
            };
            if (page != null) LoggerFactory.getLogger("TheLadsCore").info("Lads integrated settings ready: {} ({} controls)", id, page.options().size());
            return page;
        } catch (Exception | LinkageError error) {
            LoggerFactory.getLogger("TheLadsCore").warn("Lads settings adapter unavailable for {}; retaining the engine editor", id, error);
            return null;
        }
    }
    private static Class<?> type(String name) throws ClassNotFoundException { return Class.forName(name); }
    private static Builder fields(Builder builder, Object target, String... names) throws Exception {
        for (String name : names) builder.field(target, name);
        return builder;
    }
    private static Builder switches(Builder builder, Object target, String... excluded) throws Exception {
        Set<String> skip = Set.of(excluded);
        Class<?> type = target instanceof Class<?> c ? c : target.getClass();
        for (Field field : type.getFields()) {
            if (Modifier.isFinal(field.getModifiers()) || skip.contains(field.getName())) continue;
            if (field.getType() == boolean.class || field.getType().isEnum()) builder.field(target, field.getName());
        }
        return builder;
    }
    private static Page appleSkin() throws Exception {
        Object config = get(type("squeek.appleskin.ModConfig"), "INSTANCE");
        return switches(new Builder("AppleSkin", () -> call(config, "save")), config)
            .number(config, "maxHudOverlayFlashAlpha", 0, 1, .05).build();
    }
    private static Page skinLayers() throws Exception {
        Object mod = get(type("dev.tr7zw.skinlayers.SkinLayersModBase"), "instance");
        Object config = get(mod, "config");
        return switches(new Builder("3D Skin Layers", () -> { afterApply.accept("skinlayers3d"); call(mod, "writeConfig"); }), config)
            .number(config, "renderDistanceLOD", 5, 40, 1)
            .number(config, "baseVoxelSize", 1.001, 1.4, .001)
            .number(config, "bodyVoxelWidthSize", 1.001, 1.4, .001)
            .number(config, "headVoxelSize", 1.001, 1.25, .001)
            .help("Layer switches and sizing apply when saved. Restart for compatibility changes. Advanced includes all sizing controls.")
            .build();
    }
    private static Page animations() throws Exception {
        Object mod = get(type("dev.tr7zw.notenoughanimations.NEAnimationsLoader"), "INSTANCE");
        Object config = get(mod, "config");
        return switches(new Builder("Not Enough Animations", () -> call(mod, "writeConfig")), config)
            .help("Animation switches apply when saved. Advanced includes movement tuning and item lists.").build();
    }
    private static Page entityCulling() throws Exception {
        Object mod = get(type("dev.tr7zw.entityculling.EntityCullingModBase"), "instance");
        Object config = get(mod, "config");
        return fields(new Builder("Entity Culling", () -> call(mod, "writeConfig")), config,
            "renderNametagsThroughWalls", "tickCulling", "disableF3", "skipEntityCulling", "skipBlockEntityCulling",
            "blockEntityFrustumCulling", "forceDisplayCulling", "solidLeaves")
            .help("Culling switches apply when saved. Advanced includes entity exceptions. Aggressive culling can hide modded objects.").build();
    }
    private static Page tabTweaks() throws Exception {
        Object handler = get(type("dev.microcontrollers.tabtweaks.config.TabTweaksConfig"), "CONFIG");
        Class<?> api = type("dev.isxander.yacl3.config.v2.api.ConfigClassHandler");
        Object config = api.getMethod("instance").invoke(handler);
        return switches(new Builder("Tab Tweaks", () -> api.getMethod("save").invoke(handler)), config)
            .help("Player-list switches apply when saved. Advanced includes layout, colors and numerical limits.").build();
    }
    private static Page capes() throws Exception {
        Object mod = get(type("me.cael.capes.Capes"), "INSTANCE");
        Object config = call(mod, "getCONFIG");
        Builder b = new Builder("Capes", () -> call(config, "save"));
        for (String name : List.of("ClientCapeType", "EnableOptifine", "EnableLabyMod", "EnableMinecraftCapesMod", "EnableCosmetica", "EnableCloaksPlus", "EnableElytraTexture")) {
            Method getter = config.getClass().getMethod("get" + name);
            Method setter = config.getClass().getMethod("set" + name, getter.getReturnType());
            b.property(label(name), getter.getReturnType(), () -> getter.invoke(config), value -> setter.invoke(config, value));
        }
        return b.help("Save cape provider preferences here. Rejoin to refresh already cached cape textures.").build();
    }
    private static Page betterF3() throws Exception {
        Class<?> config = type("me.cominixo.betterf3.config.GeneralOptions");
        Runnable save = (Runnable)get(type("me.cominixo.betterf3.config.ModConfigFile"), "saveRunnable");
        return switches(new Builder("BetterF3", save::run), config)
            .help("Debug overlay switches apply when saved. Advanced includes module layout, colors, scaling and animation speed.").build();
    }
    private static Page autoReconnect() throws Exception {
        Class<?> api = type("dev.terminalmc.autoreconnectrf.config.Config");
        Object config = call(api, "options");
        Builder builder = fields(new Builder("AutoReconnect", () -> call(api, "save")), config,
            "infinite", "conditionType");
        // The 26.2 release adds initial-attempt handling; 1.21.11 does not expose that preference.
        try { config.getClass().getField("initial"); builder.field(config, "initial"); } catch (NoSuchFieldException ignored) {}
        return builder.help("Reconnect behavior applies when saved. Advanced includes delays and disconnect conditions. Existing commands are preserved.").build();
    }
    private static Page betterStats() throws Exception {
        Object config = call(type("com.thecsdev.betterstats.BetterStats"), "getConfig");
        Builder builder = new Builder("Better Statistics", () -> call(config, "saveToFile"));
        for (String[] setting : new String[][] {
            {"Mobs follow cursor", "getGuiMobsFollowCursor", "setGuiMobsFollowCursor"},
            {"Show announcement messages", "allowsChatPsaMessages", "setAllowChatPsaMessages"},
            {"Register commands (restart)", "canRegisterCommands", "setRegisterCommands"}
        }) {
            Method getter = config.getClass().getMethod(setting[1]);
            Method setter = config.getClass().getMethod(setting[2], boolean.class);
            builder.property(setting[0], boolean.class, () -> getter.invoke(config), value -> setter.invoke(config, value));
        }
        try {
            Method getter = config.getClass().getMethod("experimentsEnabled");
            Method setter = config.getClass().getMethod("setExperimentsEnabled", boolean.class);
            builder.property("Experimental features", boolean.class, () -> getter.invoke(config), value -> setter.invoke(config, value));
        } catch (NoSuchMethodException absentOn12111) { /* Added in the pinned 26.2 release. */ }
        return builder.help("Viewer preferences apply when saved. Command registration takes effect after restarting Minecraft.").build();
    }
}
