package com.thelads.core.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.slf4j.LoggerFactory;

/**
 * Cheats stay as the host set them, on every version.
 *
 * <p>Essential keeps its own cheats switch for each world (essential-world-settings.json in the world folder). It makes it
 * once, from level.dat, the first time it sees the world (a new world it sees before its level.dat is written gets "off"),
 * and from then on puts it back every time the world opens: 1.8.9 writes it into the world, 26.x answers
 * LevelSettings.allowCommands() with it, which also decides what level.dat saves. Minecraft's own switches (Create World,
 * World Options, Open to LAN) never change it, so cheats they turned on were gone the next time the world opened, for
 * the host and for everyone who joined. The adapters make the world's own switch win when the world opens and set
 * Essential's switch whenever the host changes the world's.
 *
 * <p>Minecraft keeps joined players' cheats (Open to LAN's Allow Cheats, 26.2's Other Players commands, 26.3's guest
 * command access) only while the world is open; the host's last choice is kept in the world folder (lads-cheats.properties).
 */
public final class WorldCheats {
    static final String FILE = "lads-cheats.properties";
    private static final Map<String, Method> METHODS = new HashMap<>();
    private static Class<?> essentialClass;
    private static boolean essentialBroken, essentialAbsent;

    private WorldCheats() {}

    /** The host's last choice for joined players' cheats in this world folder, or null when the host never chose. */
    public static Boolean guests(Path world) {
        Path file = world.resolve(FILE);
        if (!Files.isRegularFile(file)) return null;
        Properties saved = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            saved.load(in);
        } catch (IOException failure) {
            LoggerFactory.getLogger("TheLadsCore").warn("Lads cheats: could not read {}", file, failure);
            return null;
        }
        String value = saved.getProperty("guests");
        return value == null ? null : Boolean.valueOf(value.trim());
    }

    /** Keeps the host's choice for joined players' cheats in this world folder. */
    public static void guests(Path world, boolean allowed) {
        if (Boolean.valueOf(allowed).equals(guests(world))) return;
        Properties saved = new Properties();
        saved.setProperty("guests", Boolean.toString(allowed));
        try (OutputStream out = Files.newOutputStream(world.resolve(FILE))) {
            saved.store(out, "Lads Client: whether players who join this world may use cheats, as the host last chose");
        } catch (IOException failure) {
            LoggerFactory.getLogger("TheLadsCore").warn("Lads cheats: could not save the joined players' choice in {}", world, failure);
        }
    }

    /** Essential's cheats switch for the open world, or null without Essential (or before it has the world). */
    public static Boolean essential() {
        try {
            Object settings = untracked(call(world(), "getGameSettings"));
            return settings == null ? null : (Boolean) call(settings, "getCheats");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            giveUp(failure);
            return null;
        }
    }

    /** Sets Essential's cheats switch for the open world (its file, and on 26.x the value it answers at once); false without it. */
    public static boolean essential(boolean cheats) {
        try {
            Object world = world();
            if (world == null) return false;
            Method update = method(world.getClass(), "updateLocalGameSettings", 1);
            Class<?> function = update.getParameterTypes()[0]; // kotlin.jvm.functions.Function1, from Essential's loader
            update.invoke(world, Proxy.newProxyInstance(function.getClassLoader(), new Class<?>[] {function}, (proxy, called, args) -> {
                switch (called.getName()) {
                    case "invoke": {
                        Object settings = args[0];
                        return method(settings.getClass(), "copy", 5).invoke(settings, call(settings, "getGameMode"),
                            call(settings, "getDifficulty"), call(settings, "getDifficultyLocked"), cheats, call(settings, "getOps"));
                    }
                    case "hashCode": return System.identityHashCode(proxy);
                    case "equals": return proxy == args[0];
                    default: return "Lads cheats " + cheats;
                }
            }));
            // 26.x: Essential applies its switch on the server thread a moment later; it answers with the new one from now on.
            Object manager = untracked(call(instance(), "getIntegratedServerManager"));
            Method applied = manager == null ? null : find(manager.getClass(), "setAppliedCheatsEnabled", 1);
            if (applied != null) applied.invoke(manager, cheats);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            giveUp(failure);
            return false;
        }
    }

    private static Object instance() throws ReflectiveOperationException {
        if (essentialBroken || essentialAbsent) return null;
        if (essentialClass == null) {
            try {
                essentialClass = Class.forName("gg.essential.Essential");
            } catch (ClassNotFoundException absent) {
                essentialAbsent = true;
                return null;
            }
        }
        return call(essentialClass, null, "getInstance");
    }

    /** Essential's WorldManager of the world the integrated server has open. */
    private static Object world() throws ReflectiveOperationException {
        return untracked(call(call(instance(), "getWorldsManager"), "getIntegratedServerWorld"));
    }

    private static Object untracked(Object state) throws ReflectiveOperationException {
        return call(state, "getUntracked");
    }

    private static Object call(Object target, String name) throws ReflectiveOperationException {
        return target == null ? null : call(target.getClass(), target, name);
    }

    private static Object call(Class<?> type, Object target, String name) throws ReflectiveOperationException {
        return method(type, name, 0).invoke(target);
    }

    private static Method method(Class<?> type, String name, int parameters) throws NoSuchMethodException {
        Method found = find(type, name, parameters);
        if (found == null) throw new NoSuchMethodException(type.getName() + "." + name);
        return found;
    }

    /** The public instance or static method; Essential's Kotlin classes are often not public, so it is made accessible. */
    private static synchronized Method find(Class<?> type, String name, int parameters) {
        String key = type.getName() + '#' + name + '/' + parameters;
        if (METHODS.containsKey(key)) return METHODS.get(key);
        Method found = null;
        for (Method candidate : type.getMethods()) {
            if (!candidate.getName().equals(name) || candidate.getParameterCount() != parameters) continue;
            candidate.setAccessible(true);
            found = candidate;
            break;
        }
        METHODS.put(key, found);
        return found;
    }

    private static void giveUp(Throwable failure) {
        if (essentialBroken) return;
        essentialBroken = true;
        LoggerFactory.getLogger("TheLadsCore").warn("Lads cheats: Essential's world settings could not be used; its cheats switch is left alone", failure);
    }
}
