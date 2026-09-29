package com.thelads.core.config;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import static com.thelads.core.config.IntegratedSettings.*;

/** Public APIs verified in the pinned 1.21.11/26.2 engines; never writes parallel config files. */
public final class AdvancedIntegrations {
    private AdvancedIntegrations() {}

    public static IntegratedSettings.Page open(String modId) throws Exception {
        return switch (modId) {
            case "dynamic_fps" -> dynamicFps();
            case "lambdynlights" -> dynamicLights();
            case "sodium" -> sodium();
            case "jei" -> jei();
            default -> null;
        };
    }

    private static Page dynamicFps() throws Exception {
        Object config = get(Class.forName("dynamic_fps.impl.config.DynamicFPSConfig"), "INSTANCE");
        Class<?> engine = Class.forName("dynamic_fps.impl.DynamicFPSMod");
        Method apply = publicMethod(engine, "onConfigChanged");
        Builder b = new Builder("Dynamic FPS", () -> apply.invoke(null))
            .help("Applied live and saved by Dynamic FPS. FPS 260 means unlimited; 0 stops background rendering. Advanced includes sound and battery settings.");
        property(b, "Enabled", config, "enabled", "setEnabled");
        property(b, "Uncap menu frame rate", config, "uncapMenuFrameRate", "setUncapMenuFrameRate");
        property(b, "Ignore initial click", config, "ignoreInitialClick", "setIgnoreInitialClick");
        Object idle = call(config, "idle");
        number(b, "Idle timeout (seconds)", idle, "timeout", "setTimeout", 0, 1800, 1);
        property(b, "Idle condition", idle, "condition", "setCondition");
        Class<?> stateType = Class.forName("dynamic_fps.impl.PowerState");
        Method getState = publicMethod(config.getClass(), "get", stateType);
        for (String state : new String[] {"UNFOCUSED", "INVISIBLE", "ABANDONED"}) {
            Object stateConfig = getState.invoke(config, get(stateType, state));
            number(b, label(state) + " FPS", stateConfig, "frameRateTarget", "setFrameRateTarget", 0, 260, 1);
        }
        return b.build();
    }

    private static Page dynamicLights() throws Exception {
        Object engine = get(Class.forName("dev.lambdaurora.lambdynlights.LambDynLights"), "INSTANCE");
        Object config = get(engine, "config");
        Method save = publicMethod(config.getClass(), "save");
        Builder b = new Builder("LambDynamicLights", () -> save.invoke(config))
            .help("Applied live using the lighting engine's refresh callbacks and saved to its own configuration. Advanced includes distance and scheduling settings.");
        property(b, "Lighting quality", config, "getDynamicLightsMode", "setDynamicLightsMode");
        property(b, "Creeper lighting", config, "getCreeperLightingMode", "setCreeperLightingMode");
        property(b, "TNT lighting", config, "getTntLightingMode", "setTntLightingMode");
        for (String[] setting : new String[][] {
            {"Entity light sources", "getEntitiesLightSource"}, {"Player light source", "getSelfLightSource"},
            {"Water-sensitive lights", "getWaterSensitiveCheck"}, {"Background adaptive ticking", "getBackgroundAdaptiveTicking"},
            {"Beacon beam lighting", "getBeamLighting"}, {"Firefly lighting", "getFireflyLighting"},
            {"Guardian laser lighting", "getGuardianLaser"}, {"Sonic boom lighting", "getSonicBoomLighting"},
            {"Glowing effect lighting", "getGlowingEffectLighting"}
        }) {
            Object entry = call(config, setting[1]);
            Method read = publicMethod(entry.getClass(), "get");
            Method set = publicMethod(entry.getClass(), "set", Object.class);
            b.property(setting[0], Boolean.class, () -> read.invoke(entry), value -> set.invoke(entry, value));
        }
        return b.build();
    }

    private static Page sodium() throws Exception {
        Object options = call(Class.forName("net.caffeinemc.mods.sodium.client.SodiumClientMod"), "options");
        Object model = get(Class.forName("net.caffeinemc.mods.sodium.client.config.ConfigManager"), "CONFIG");
        return sodiumPage(options, model);
    }

    // Package-private to exercise the real staged/save path with a disk-backed test engine.
    static Page sodiumPage(Object options, Object model) throws Exception {
        if ((Boolean) call(options, "isReadOnly") || model == null) return null;
        Method pending = publicMethod(model.getClass(), "anyOptionChanged");
        if ((Boolean) pending.invoke(model)) return null;
        Method save = publicMethod(options.getClass(), "writeToDisk", options.getClass());
        // This option has no reload/restart flag in BOTH 0.8.14 and 0.9.1. Other performance
        // options require different native hooks (not all public on 0.8.14), so retain Advanced.
        return new Builder("Sodium", () -> {
            if ((Boolean) pending.invoke(model))
                throw new IllegalStateException("Sodium has pending changes. Apply or discard them in Advanced first.");
            // Disk persistence is the final operation. No global model reset may fail AFTER
            // this succeeds and cause Page.apply to roll back only the live field.
            save.invoke(null, options);
            // Both pinned VideoSettingsScreen constructors resetAllOptionsFromBindings on open.
            // Leave that synchronization to Sodium, preserving its other staged option state.
        }).help("Entity visibility culling applies immediately. Advanced contains all video, chunk-building and graphics settings.")
            .field(get(options, "performance"), "useEntityCulling", "Entity visibility culling")
            .build();
    }

    private static final Map<String, String> JEI_BOOLEANS = Map.ofEntries(
        Map.entry("appearance.centerSearch", "Center search bar"),
        Map.entry("appearance.toastReflowEnabled", "Move toasts away from ingredients"),
        Map.entry("bookmarks.addBookmarksToFrontEnabled", "Add bookmarks at the front"),
        Map.entry("bookmarks.bookmarkOutputAsRecipe", "Bookmark recipe outputs as recipes"),
        Map.entry("bookmarks.dragToRearrangeBookmarksEnabled", "Drag to rearrange bookmarks"),
        Map.entry("tooltips.showCreativeTabNamesEnabled", "Show creative tab names"),
        Map.entry("tooltips.tagContentTooltipEnabled", "Show tag contents in tooltips"),
        Map.entry("tooltips.hideSingleTagContentTooltipEnabled", "Hide single-entry tag contents"),
        Map.entry("tooltips.enableRecipesGuiIngredientsSummary", "Show recipe ingredient summary")
    );

    private static Page jei() throws Exception {
        Optional<?> runtime = (Optional<?>) call(Class.forName("mezz.jei.common.Internal"), "getOptionalJeiRuntime");
        // The supported public config manager is available through a running JEI runtime.
        if (runtime.isEmpty()) return null;
        Object manager = publicMethod(Class.forName("mezz.jei.api.runtime.IJeiRuntime"), "getConfigManager").invoke(runtime.get());
        Method files = publicMethod(Class.forName("mezz.jei.api.runtime.config.IJeiConfigManager"), "getConfigFiles");
        Method categories = publicMethod(Class.forName("mezz.jei.api.runtime.config.IJeiConfigFile"), "getCategories");
        Class<?> categoryApi = Class.forName("mezz.jei.api.runtime.config.IJeiConfigCategory");
        Method categoryName = publicMethod(categoryApi, "getName"), values = publicMethod(categoryApi, "getConfigValues");
        Class<?> valueApi = Class.forName("mezz.jei.api.runtime.config.IJeiConfigValue");
        Method valueName = publicMethod(valueApi, "getName");
        Method read = publicMethod(valueApi, "getValue"), set = publicMethod(valueApi, "set", Object.class);
        Method serializer = publicMethod(valueApi, "getSerializer");
        Method valid = publicMethod(Class.forName("mezz.jei.api.runtime.config.IJeiConfigValueSerializer"), "isValid", Object.class);
        Builder b = new Builder("JEI", () -> {})
            .help("Applied live; JEI queues its own configuration save. These controls are available after loading a world. Advanced contains all JEI settings.");
        // Invoke public interfaces, not concrete classes: implementations may be non-public.
        for (Object file : (Collection<?>) files.invoke(manager)) {
            for (Object category : (Collection<?>) categories.invoke(file)) {
                String categoryId = (String) categoryName.invoke(category);
                for (Object value : (Collection<?>) values.invoke(category)) {
                    String name = JEI_BOOLEANS.get(categoryId + "." + valueName.invoke(value));
                    if (name == null || !(read.invoke(value) instanceof Boolean)) continue;
                    b.property(name, Boolean.class, () -> read.invoke(value), updated -> {
                        if (!(Boolean) valid.invoke(serializer.invoke(value), updated))
                            throw new IllegalArgumentException("JEI rejected " + name);
                        // set returns false for both invalid AND unchanged values; read-back distinguishes them.
                        set.invoke(value, updated);
                        if (!Objects.equals(read.invoke(value), updated))
                            throw new IllegalStateException("JEI did not apply " + name);
                    });
                }
            }
        }
        Page page = b.build();
        return page.options().isEmpty() ? null : page;
    }

    private static void property(Builder b, String label, Object target, String getter, String setter) throws Exception {
        Method read = publicMethod(target.getClass(), getter);
        Method write = publicMethod(target.getClass(), setter, read.getReturnType());
        b.property(label, read.getReturnType(), () -> read.invoke(target), value -> write.invoke(target, value));
    }

    private static void number(Builder b, String label, Object target, String getter, String setter,
                               double min, double max, double step) throws Exception {
        Method read = publicMethod(target.getClass(), getter);
        Method write = publicMethod(target.getClass(), setter, read.getReturnType());
        b.number(label, read.getReturnType(), () -> read.invoke(target), value -> write.invoke(target, value), min, max, step);
    }

    static Method publicMethod(Class<?> api, String name, Class<?>... parameters) throws ReflectiveOperationException {
        Method method = api.getMethod(name, parameters);
        if (!Modifier.isPublic(method.getDeclaringClass().getModifiers()))
            throw new IllegalAccessException("Upstream method has no public declaring class: " + method);
        return method;
    }
}
