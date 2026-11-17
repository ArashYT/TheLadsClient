package com.thelads.core.v1_20_1.net;

import com.mojang.blaze3d.platform.InputConstants;
import com.thelads.core.client.ladsapi.ServerSettings;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Option;
import com.thelads.core.config.SliderOption;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;

/**
 * Vanilla options a LadsClientAPI server may set or limit (ServerSettings "Minecraft"), asked by OptionInstance.get (ServerOptionMixin).
 * Besides the five named ones, every option and keybind (mods' keys too) by its options.txt name: "renderClouds", "key_key.jump".
 */
public final class NativeServerOptions {
    /** Options.save() is writing options.txt: it gets the player's own values (ServerOptionsSaveMixin). */
    public static volatile boolean saving;
    /** Never a server's: the window, the graphics backend, the sound device, telemetry and first-run screens. */
    private static final Set<String> NEVER = Set.of("fullscreen", "exclusiveFullscreen", "macFullscreenMenuVisibility", "preferredGraphicsBackend",
        "soundDevice", "telemetryOptInExtra", "onboardAccessibility");
    /** Built once by catalog(), then only read: option to options.txt name, and a choice option's values. */
    private static volatile Map<Object, String> names = Map.of();
    private static volatile Map<Object, List<Object>> choiceValues = Map.of();
    /** The effective value each named option had after the last rule change, to rerun its callback when that changes. */
    private static final Map<Object, Object> LAST = new IdentityHashMap<>();
    /** The player's own key for each keybind a rule moved. */
    private static final Map<KeyMapping, String> OWN_KEYS = new HashMap<>();

    private NativeServerOptions() {}

    public static void install() {
        ServerSettings.vanillaHooked(NativeServerOptions::catalog, () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) mc.execute(NativeServerOptions::rulesChanged);
        });
    }

    /** The value the server's rules give this option, or null to leave it alone (saving, or not an option a server may touch). */
    public static Object effective(Object option, Object own) {
        Minecraft mc = Minecraft.getInstance();
        Options o = mc == null ? null : mc.options;
        if (o == null || saving) return null;
        String name = names.get(option);
        if (name != null && ServerSettings.hasVanillaRule(name)) {
            Object value = generic(option, name, own);
            if (value != null) return value;
        }
        if (option == o.fov()) return (int) Math.round(ServerSettings.vanilla("FOV", (Integer) own));
        if (option == o.gamma()) {
            double percent = (Double) own * 100.0, effective = ServerSettings.vanilla("Brightness", percent);
            return effective == percent ? null : effective / 100.0; // untouched: the exact own value, not one the maths rounded
        }
        if (option == o.renderDistance()) return (int) Math.round(ServerSettings.vanilla("Render Distance", (Integer) own));
        if (option == o.bobView()) return ServerSettings.vanilla("View Bobbing", (Boolean) own);
        if (option == o.entityShadows()) return ServerSettings.vanilla("Entity Shadows", (Boolean) own);
        return null;
    }

    private static Object generic(Object option, String name, Object own) {
        List<Object> choices = choiceValues.get(option);
        if (choices != null) {
            int index = choices.indexOf(own), effective = ServerSettings.vanillaChoice(name, index);
            return effective == index || effective < 0 ? null : choices.get(effective);
        }
        if (own instanceof Boolean b) return ServerSettings.vanilla(name, b.booleanValue());
        if (own instanceof Integer i) return (int) Math.round(ServerSettings.vanilla(name, (double) i));
        if (own instanceof Double d) {
            double effective = ServerSettings.vanilla(name, d.doubleValue());
            return effective == d ? null : effective;
        }
        return null;
    }

    /** Every option Options.processOptions names, as rules see them; built once, on the first policy naming "Minecraft". */
    private static List<Option> catalog() {
        Minecraft mc = Minecraft.getInstance();
        Options o = mc == null ? null : mc.options;
        if (o == null) return null;
        Map<String, OptionInstance<?>> found = new LinkedHashMap<>();
        try {
            Method process = null;
            for (Method m : Options.class.getDeclaredMethods()) {
                Class<?>[] params = m.getParameterTypes();
                if (params.length != 1 || !params[0].isInterface() || params[0].getEnclosingClass() != Options.class) continue;
                if (process == null || params[0].getMethods().length > process.getParameterTypes()[0].getMethods().length) process = m;
            }
            if (process == null) return null;
            Class<?> access = process.getParameterTypes()[0];
            Object recorder = Proxy.newProxyInstance(access.getClassLoader(), new Class<?>[] {access}, (proxy, m, args) -> {
                if (m.getDeclaringClass() == Object.class) {
                    return m.getName().equals("hashCode") ? 0 : m.getName().equals("equals") ? proxy == args[0] : "recorder";
                }
                if (args != null && args.length == 2 && args[1] instanceof OptionInstance<?> inst) {
                    found.put((String) args[0], inst);
                    return null;
                }
                return args != null && args.length >= 2 ? args[1] : null; // every plain field keeps its value
            });
            process.setAccessible(true);
            process.invoke(o, recorder);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
        List<Option> options = new ArrayList<>();
        Map<Object, String> named = new IdentityHashMap<>();
        Map<Object, List<Object>> choices = new IdentityHashMap<>();
        for (Map.Entry<String, OptionInstance<?>> e : found.entrySet()) {
            String name = e.getKey();
            OptionInstance<?> inst = e.getValue();
            if (NEVER.contains(name)) continue;
            Option option = describe(name, inst, choices);
            if (option != null) {
                options.add(option);
                named.put(inst, name);
            }
        }
        choiceValues = choices;
        names = named;
        for (KeyMapping key : o.keyMappings) options.add(new ServerSettings.KeyOption("key_" + key.getName(), key.saveString()));
        return options;
    }

    private static Option describe(String name, OptionInstance<?> inst, Map<Object, List<Object>> choiceValues) {
        Object own = inst.get(), values = inst.values();
        if (own instanceof Boolean b) return new BoolOption(name, b);
        if (values instanceof OptionInstance.IntRange r) return new SliderOption(name, (Integer) own, r.minInclusive(), r.maxInclusive(), 1);
        if (values instanceof OptionInstance.ClampingLazyMaxIntRange r) {
            return new SliderOption(name, (Integer) own, r.minInclusive(), r.maxInclusive(), 1);
        }
        if (values instanceof OptionInstance.UnitDouble) return new SliderOption(name, (Double) own, 0, 1, 0.01);
        List<?> list = null;
        if (values instanceof OptionInstance.Enum<?> en) list = en.values();
        else if (values instanceof OptionInstance.AltEnum<?> alt) list = alt.values();
        else if (values instanceof OptionInstance.LazyEnum<?> lazy) list = lazy.values().get();
        if (list == null || list.isEmpty()) return null;
        String[] choices = new String[list.size()];
        for (int i = 0; i < choices.length; i++) {
            Object v = list.get(i);
            choices[i] = v instanceof Enum<?> en ? en.name().toLowerCase(Locale.ROOT) : String.valueOf(v);
        }
        choiceValues.put(inst, new ArrayList<>(list));
        return new DropdownOption(name, Math.max(0, list.indexOf(own)), choices);
    }

    /** Render thread: options whose effective value changed rerun vanilla's callback (chunks, sound volume, GUI scale), keys rebind. */
    private static void rulesChanged() {
        Minecraft mc = Minecraft.getInstance();
        Options o = mc.options;
        if (o == null) return;
        boolean changed = false;
        for (Object option : names.keySet()) {
            OptionInstance<?> inst = (OptionInstance<?>) option;
            Object now = inst.get(), before = LAST.containsKey(option) ? LAST.get(option) : own(inst);
            LAST.put(option, now);
            if (!now.equals(before)) {
                changed = true;
                callback(inst, now);
            }
        }
        boolean rebound = false;
        for (KeyMapping key : o.keyMappings) {
            String forced = ServerSettings.vanillaKey("key_" + key.getName());
            try {
                if (forced != null) {
                    String own = key.saveString();
                    if (forced.equals(own)) continue;
                    OWN_KEYS.putIfAbsent(key, own);
                    key.setKey(InputConstants.getKey(forced));
                    rebound = true;
                } else if (OWN_KEYS.containsKey(key)) {
                    key.setKey(InputConstants.getKey(OWN_KEYS.remove(key)));
                    rebound = true;
                }
            } catch (RuntimeException ignored) {} // a key name this version does not know
        }
        if (rebound) KeyMapping.resetMapping();
        if (changed && mc.player != null) o.broadcastOptions(); // view distance, skin parts, main hand
    }

    /** The player's own value: what the option held before the first rule. */
    private static Object own(OptionInstance<?> inst) {
        boolean was = saving;
        saving = true;
        try {
            return inst.get();
        } finally {
            saving = was;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void callback(OptionInstance<?> inst, Object value) {
        try {
            for (Field f : OptionInstance.class.getDeclaredFields()) {
                if (f.getType() != java.util.function.Consumer.class) continue;
                f.setAccessible(true);
                ((java.util.function.Consumer) f.get(inst)).accept(value);
                return;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {}
    }

    /** options.txt gets the player's own keys: a key the player rebound under a rule becomes their own. */
    public static void beginSave() {
        saving = true;
        for (Map.Entry<KeyMapping, String> e : OWN_KEYS.entrySet()) {
            KeyMapping key = e.getKey();
            String forced = ServerSettings.vanillaKey("key_" + key.getName());
            if (forced != null && !forced.equals(key.saveString())) e.setValue(key.saveString());
            try {
                key.setKey(InputConstants.getKey(e.getValue()));
            } catch (RuntimeException ignored) {}
        }
    }

    public static void endSave() {
        saving = false;
        for (KeyMapping key : OWN_KEYS.keySet()) {
            String forced = ServerSettings.vanillaKey("key_" + key.getName());
            try {
                if (forced != null) key.setKey(InputConstants.getKey(forced));
            } catch (RuntimeException ignored) {}
        }
    }
}
