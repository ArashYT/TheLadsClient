package com.thelads.core.v1_20_1.perf;

import com.thelads.core.config.ConfigManager;
import com.thelads.core.v1_20_1.compat.ModPresence1201;
import com.thelads.core.perf.EnumValuesRewriter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Jasione on Fabric: Knot hands every game and mod class to Mixin's transformer before defining it. That transformer is
 * wrapped, so EnumValuesRewriter sees each class after its mixins (whose injection points may be values() calls) applied.
 * Set up once before the game starts; the Jasione module's saved state decides it, so a toggle applies after a restart.
 */
public final class EnumValuesHook1201 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static boolean installed, reported;

    private EnumValuesHook1201() {}
    /** Jasione hooks Fabric's Knot class loader; under Forge (Connector) class loading is Forge's and the hook is not available. */
    public static boolean available() {
        if (ModPresence1201.onForge()) {
            LOGGER.info("Jasione: not available under Forge/Connector; Enum#values() calls stay as they are");
            return false;
        }
        return true;
    }


    public static void install() {
        if (ModPresence1201.loaded("jasione") || !available() || !ConfigManager.savedEnabled("Jasione", true)) return;
        try {
            ClassLoader knot = EnumValuesHook1201.class.getClassLoader();
            Object delegate = field(knot.getClass(), "delegate").get(knot);
            Field transformerField = field(delegate.getClass(), "mixinTransformer");
            Object mixin = transformerField.get(delegate);
            Predicate<String> isEnum = EnumValuesRewriter.enumCheck(name -> read(knot, name));
            Class<?> type = transformerField.getType();
            transformerField.set(delegate, Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
                Object result;
                try {
                    result = method.invoke(mixin, args);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
                if (result instanceof byte[] bytes && method.getName().equals("transformClassBytes")) {
                    byte[] rewritten = EnumValuesRewriter.rewrite(bytes, isEnum, true);
                    if (rewritten != null) return rewritten;
                }
                return result;
            }));
            installed = true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Jasione could not attach to the class loader; Enum#values() calls stay as they are", e);
        }
    }

    /** The one-line count, once, when the game has finished loading. */
    public static void reportOnce() {
        if (!installed || reported) return;
        reported = true;
        LOGGER.info(EnumValuesRewriter.summary());
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static byte[] read(ClassLoader loader, String internalName) {
        try (InputStream in = loader.getResourceAsStream(internalName + ".class")) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }
}
