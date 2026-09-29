package com.thelads.core.config;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.Objects;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import static com.thelads.core.config.IntegratedSettings.*;

/** Public live configuration APIs verified against the pinned 1.21.11 and 26.2 engines. */
public final class AdditionalIntegrations {
    private AdditionalIntegrations() {}

    public static Page open(String id) throws Exception {
        return switch (id) {
            case "custom-crosshair-mod" -> {
                Object mod = get(Class.forName("com.wjbaker.ccm.CustomCrosshairMod"), "INSTANCE");
                yield crosshair(() -> call(mod, "properties"), () -> {
                    if (!Boolean.TRUE.equals(call(call(mod, "configManager"), "write")))
                        throw new IOException("Custom Crosshair could not save its configuration. Check the game config directory is writable.");
                });
            }
            case "raised" -> {
                Class<?> config = Class.forName("dev.yurisuika.raised.config.Config");
                yield raised(() -> call(config, "getOptions"), () -> call(config, "saveConfig"));
            }
            case "screenshot_viewer" -> {
                Object mod = call(Class.forName("io.github.lgatodu47.screenshot_viewer.ScreenshotViewer"), "getInstance");
                Object config = call(mod, "getConfig");
                Class<?> access = Class.forName("io.github.lgatodu47.catconfig.ConfigAccess");
                Class<?> option = Class.forName("io.github.lgatodu47.catconfig.ConfigOption");
                Class<?> options = Class.forName("io.github.lgatodu47.screenshot_viewer.config.ScreenshotViewerOptions");
                Class<?> maps = Class.forName("io.github.lgatodu47.catconfig.ConfigValueMap");
                Object side = get(Class.forName("io.github.lgatodu47.catconfigmc.MinecraftConfigSides"), "CLIENT");
                Class<?> loader = Class.forName("net.fabricmc.loader.api.FabricLoader");
                Path directory = (Path)loader.getMethod("getConfigDir").invoke(call(loader, "getInstance"));
                // CatConfig's path accessor is protected. This is the pinned constructor's exact
                // path; only verify it after the engine writes, never use it as a config fallback.
                Path file = directory.resolve("screenshot_viewer-" + call(side, "sideName") + ".json");
                yield screenshots(config, access, option, options,
                    () -> {
                        Object values = maps.getMethod("create", Class.forName("io.github.lgatodu47.catconfig.ConfigSide"),
                            Class.forName("io.github.lgatodu47.catconfig.ConfigOptionAccess")).invoke(null, side, get(options, "OPTIONS"));
                        verifyScreenshotSave(config, access, option, options, maps, values, file, () -> call(config, "writeToFile"));
                        call(call(mod, "getThumbnailManager"), "configUpdated");
                    });
            }
            case "xaerominimap", "xaeroworldmap" -> {
                boolean minimap = id.equals("xaerominimap");
                Object mod = get(Class.forName(minimap ? "xaero.common.HudMod" : "xaero.map.WorldMap"), "INSTANCE");
                Object channel = call(mod, minimap ? "getHudConfigs" : "getConfigs");
                Object manager = call(channel, "getClientConfigManager");
                Object profile = call(manager, "getCurrentProfile");
                if (profile == null) yield null;
                Class<?> option = Class.forName("xaero.lib.common.config.option.ConfigOption");
                Class<?> indexed = Class.forName("xaero.lib.common.config.option.IndexedConfigOption");
                Class<?> options = Class.forName(minimap
                    ? "xaero.hud.minimap.common.config.option.MinimapProfiledConfigOptions"
                    : "xaero.map.common.config.option.WorldMapProfiledConfigOptions");
                Object io = call(channel, "getClientConfigProfileIO");
                Method save = io.getClass().getMethod("save", Class.forName("xaero.lib.common.config.profile.ConfigProfile"));
                yield xaero(minimap ? "Xaero Minimap" : "Xaero World Map", manager, profile, option, indexed, options,
                    () -> save.invoke(io, profile));
            }
            default -> null;
        };
    }

    // These factories also allow isolated tests without initializing Minecraft or touching user config.
    static Page crosshair(Read properties, Save save) throws Exception {
        Builder b = new Builder("Custom Crosshair", save);
        wrapped(b, "Enabled", () -> call(properties.get(), "getIsModEnabled"), null);
        Read crosshair = () -> call(properties.get(), "getCrosshair");
        wrapped(b, "Style", () -> get(crosshair.get(), "style"), null);
        for (String name : List.of("isKeepDebugEnabled", "isAdaptiveColourEnabled",
                "isVisibleDefault", "isVisibleHiddenGui", "isVisibleDebug", "isVisibleThirdPerson",
                "isVisibleSpectator", "isVisibleHoldingRangedWeapon", "isVisibleHoldingThrowableItem",
                "isVisibleUsingSpyglass", "isOutlineEnabled", "isDotEnabled", "isDynamicAttackIndicatorEnabled",
                "isDynamicBowEnabled", "isHighlightHostilesEnabled", "isHighlightPassivesEnabled",
                "isHighlightPlayersEnabled", "isItemCooldownEnabled", "isRainbowEnabled",
                "isToolDamageEnabled", "isProjectileIndicatorEnabled"))
            wrapped(b, label(name.substring(2)), () -> get(crosshair.get(), name), null);
        // ShapeSettingsGuiPanel's own integer slider bounds, including percentage scale.
        for (String name : List.of("width", "height", "gap"))
            wrapped(b, label(name), () -> get(crosshair.get(), name), new int[]{0, 50});
        wrapped(b, "Thickness", () -> get(crosshair.get(), "thickness"), new int[]{1, 10});
        wrapped(b, "Rotation", () -> get(crosshair.get(), "rotation"), new int[]{0, 360});
        wrapped(b, "Scale (%)", () -> get(crosshair.get(), "scale"), new int[]{25, 500});
        for (String name : List.of("offsetX", "offsetY"))
            wrapped(b, label(name), () -> get(crosshair.get(), name), new int[]{-500, 500});
        return b.help("Save applies the crosshair's live properties. Advanced includes colors, custom drawn shapes and rainbow timing.").build();
    }

    private static void wrapped(Builder b, String label, Read property, int[] bounds) throws Exception {
        Object initial = call(property.get(), "get");
        Class<?> type = valueType(initial);
        Read read = () -> call(property.get(), "get");
        Write write = value -> {
            Object current = property.get();
            current.getClass().getMethod("set", Object.class).invoke(current, value);
        };
        if (bounds == null) b.property(label, type, read, write);
        else b.number(label, type, read, write, bounds[0], bounds[1], 1);
    }

    static Page raised(Read options, Save save) throws Exception {
        Builder b = new Builder("Raised", save);
        Map<?, ?> layers = (Map<?, ?>)call(options.get(), "getLayers");
        TreeSet<String> ids = new TreeSet<>();
        for (Object key : layers.keySet()) if (key instanceof String id) ids.add(id);
        for (String id : ids) {
            Read layer = () -> {
                Object value = ((Map<?, ?>)call(options.get(), "getLayers")).get(id);
                if (value == null) throw new IllegalStateException("Raised layer " + id + " was removed. Reopen its settings.");
                return value;
            };
            for (String axis : List.of("X", "Y")) {
                // The upstream editor's range varies with window size. Offer a bounded fine-adjustment
                // range here; Builder preserves larger custom offsets for the complete editor.
                accessors(b, label(id) + " offset " + axis,
                    () -> call(layer.get(), "getDisplacement"), "get" + axis, "set" + axis, new int[]{-64, 64});
                accessors(b, label(id) + " direction " + axis,
                    () -> call(layer.get(), "getDirection"), "get" + axis, "set" + axis, null);
            }
        }
        // Configure's setters do these same public Layer mutations followed by saveConfig. Batch the
        // save until Apply so staged controls do not cause one whole-file write per changed field.
        return b.help("Save applies layer offsets and directions. Linked layers keep their existing links. Advanced includes layer linking, textures and offsets outside -64 to 64.").build();
    }

    private static void accessors(Builder b, String label, Read target, String getter, String setter, int[] bounds) throws Exception {
        Method method = target.get().getClass().getMethod(getter);
        Class<?> type = method.getReturnType();
        Read read = () -> call(target.get(), getter);
        Write write = value -> {
            Object current = target.get();
            current.getClass().getMethod(setter, type).invoke(current, value);
        };
        if (bounds == null) b.property(label, type, read, write);
        else b.number(label, type, read, write, bounds[0], bounds[1], 1);
    }

    static Page screenshots(Object config, Class<?> access, Class<?> optionType, Object options, Save save) throws Exception {
        Builder b = new Builder("Screenshot Viewer", save);
        for (String name : screenshotOptionNames(options)) {
            int[] bounds = switch (name) {
                case "INITIAL_SCREENSHOT_AMOUNT_PER_ROW" -> new int[]{2, 8};
                case "SCREEN_SCROLL_SPEED" -> new int[]{1, 50};
                case "SCREENSHOT_ELEMENT_BACKGROUND_OPACITY" -> new int[]{0, 100};
                default -> null;
            };
            catOption(b, name, config, access, optionType, options, bounds);
        }
        return b.help("Save applies viewer preferences and refreshes its thumbnail configuration. Advanced includes folders, colors, button positions and compression.").build();
    }

    private static List<String> screenshotOptionNames(Object options) throws Exception {
        List<String> names = new ArrayList<>(List.of("SHOW_BUTTON_IN_GAME_PAUSE_MENU", "SHOW_BUTTON_ON_TITLE_SCREEN",
                "REDIRECT_SCREENSHOT_CHAT_LINKS", "DEFAULT_LIST_ORDER", "PROMPT_WHEN_DELETING_SCREENSHOT",
                "ENABLE_SCREENSHOT_ENLARGEMENT_ANIMATION", "DISPLAY_HINT_TOOLTIP", "RENDER_WIDE_PROPERTIES_BUTTON",
                "INVERT_ZOOM_DIRECTION", "SCREENSHOT_ELEMENT_TEXT_VISIBILITY", "RENDER_SCREENSHOT_ELEMENT_FONT_SHADOW",
                "INITIAL_SCREENSHOT_AMOUNT_PER_ROW", "SCREEN_SCROLL_SPEED"));
        // 26.2 replaced the scalar opacity with an ARGB color, kept in the complete editor.
        Class<?> optionsClass = options instanceof Class<?> c ? c : options.getClass();
        try {
            optionsClass.getField("SCREENSHOT_ELEMENT_BACKGROUND_OPACITY");
            names.add("SCREENSHOT_ELEMENT_BACKGROUND_OPACITY");
        } catch (NoSuchFieldException absent) {
            optionsClass.getField("SCREENSHOT_ELEMENT_BACKGROUND_COLOR");
        }
        return names;
    }

    private static void catOption(Builder b, String name, Object config, Class<?> access,
                                  Class<?> optionType, Object options, int[] bounds) throws Exception {
        Object option = get(options, name);
        Class<?> type = (Class<?>)optionType.getMethod("type").invoke(option);
        Object fallback = optionType.getMethod("defaultValue").invoke(option);
        Method getter = access.getMethod("getOrFallback", optionType, Object.class);
        Method setter = access.getMethod("put", optionType, Object.class);
        Read read = () -> getter.invoke(config, option, fallback);
        Write write = value -> {
            setter.invoke(config, option, value);
            if (!Objects.equals(value, read.get()))
                throw new IllegalStateException(label(name) + " could not be changed. Check its related settings in Advanced.");
        };
        if (bounds == null) b.property(label(name), type, read, write);
        else b.number(label(name), type, read, write, bounds[0], bounds[1], 1);
    }

    static void verifyScreenshotSave(Object config, Class<?> access, Class<?> optionType, Object options,
                                     Class<?> mapType, Object values, Path file, Save engineSave) throws Exception {
        Map<Object, Object> expected = new LinkedHashMap<>();
        Method getter = access.getMethod("getOrFallback", optionType, Object.class);
        for (String name : screenshotOptionNames(options)) {
            Object option = get(options, name);
            expected.put(option, getter.invoke(config, option, optionType.getMethod("defaultValue").invoke(option)));
        }
        engineSave.run();
        try {
            JsonElement root;
            try (Reader reader = Files.newBufferedReader(file)) { root = JsonParser.parseReader(reader); }
            for (var entry : expected.entrySet()) {
                String path = (String)optionType.getMethod("optionPath").invoke(entry.getKey());
                JsonElement node = root;
                for (String part : path.split("/")) {
                    if (part.isEmpty()) continue;
                    if (node == null || !node.isJsonObject()) throw new IOException("Missing option " + path);
                    var object = node.getAsJsonObject();
                    node = object.has("c$" + part) ? object.get("c$" + part) : object.get(part);
                }
                if (node == null) throw new IOException("Missing option " + path);
                // The engine's public reader understands both scalar and description/value nodes,
                // enum codecs and category naming; it reads into an isolated map, not live config.
                try (JsonReader reader = new JsonReader(new StringReader(node.toString()))) {
                    mapType.getMethod("readAndPut", JsonReader.class, optionType).invoke(values, reader, entry.getKey());
                }
                Object actual = mapType.getMethod("get", optionType).invoke(values, entry.getKey());
                if (!Objects.equals(entry.getValue(), actual)) throw new IOException("Saved value differs for " + path);
            }
        } catch (Exception failure) {
            throw new IOException("Screenshot Viewer could not confirm the saved settings. Check that the game config folder is writable, then retry.", failure);
        }
    }

    static Page xaero(String engine, Object manager, Object profile, Class<?> optionType,
                       Class<?> indexedType, Class<?> options, Save save) throws Exception {
        Builder b = new Builder(engine, save);
        Method raw = manager.getClass().getMethod("getRaw", optionType);
        Method effective = manager.getClass().getMethod("getEffective", optionType);
        Method setter = profile.getClass().getMethod("set", optionType, Object.class);
        for (Field field : options.getFields()) {
            if (!java.lang.reflect.Modifier.isStatic(field.getModifiers()) || !optionType.isAssignableFrom(field.getType())) continue;
            Object option = field.get(null);
            if (!xaeroEditable(manager, optionType, option)) continue;
            Object initial = raw.invoke(manager, option);
            // Null/complex values and nonuniform indexed choices retain their complete engine editor.
            if (initial == null) continue;
            Read read = () -> {
                if (call(manager, "getCurrentProfile") != profile)
                    throw new IllegalStateException("The active Xaero profile changed. Reopen these settings.");
                if (!xaeroEditable(manager, optionType, option))
                    throw new IllegalStateException(label(field.getName()) + " is now managed by the server or another map setting. Reopen these settings.");
                Object current = raw.invoke(manager, option);
                if (!Objects.equals(current, effective.invoke(manager, option)))
                    throw new IllegalStateException(label(field.getName()) + " is overridden. Use the map's complete editor.");
                return current;
            };
            // ConfigProfile.set validates values and notifies Xaero's registered change handlers,
            // including map/cache invalidation. Never write the backing option map directly.
            Write write = value -> setter.invoke(profile, option, value);
            if (initial instanceof Boolean || initial instanceof Enum<?>)
                b.property(label(field.getName()), valueType(initial), read, write);
            else if (initial instanceof Integer && indexedType.isInstance(option)) {
                List<?> values = (List<?>)indexedType.getMethod("getValidValues").invoke(option);
                int[] range = uniformIntegerRange(values);
                if (range != null) b.number(label(field.getName()), Integer.class, read, write, range[0], range[1], range[2]);
            }
        }
        return b.help("Save applies the current local map profile. Server-enforced and linked settings stay in Advanced, along with profiles, radar categories and map data.").build();
    }

    private static boolean xaeroEditable(Object manager, Class<?> optionType, Object option) throws Exception {
        Object redirector = call(manager, "getRedirectorManager");
        if (Boolean.TRUE.equals(redirector.getClass().getMethod("shouldRedirect", optionType).invoke(redirector, option))) return false;
        if (!Boolean.TRUE.equals(optionType.getMethod("isOverridable").invoke(option))) return true;
        if (Boolean.TRUE.equals(manager.getClass().getMethod("shouldIgnoreServerEnforcement", optionType).invoke(manager, option))) return true;
        Object synced = call(manager, "getServerSynced");
        return synced.getClass().getMethod("getEffective", optionType).invoke(synced, option) == null;
    }

    static int[] uniformIntegerRange(List<?> values) {
        if (values.size() < 2 || !(values.get(0) instanceof Integer first) || !(values.get(1) instanceof Integer second)) return null;
        long step = (long)second - first;
        if (step <= 0 || step > Integer.MAX_VALUE) return null;
        for (int i = 2; i < values.size(); i++)
            if (!(values.get(i) instanceof Integer n) || n.longValue() != first + step * i) return null;
        return new int[]{first, (Integer)values.getLast(), (int)step};
    }

    private static Class<?> valueType(Object value) {
        return value instanceof Enum<?> e ? e.getDeclaringClass() : value.getClass();
    }
}
