package com.thelads.core.v1_8_9.feature;

import com.thelads.core.config.ConfigManager;
import com.thelads.core.perf.EnumValuesRewriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraftforge.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.MixinEnvironment;

/**
 * Jasione on 1.8.9: a LaunchWrapper transformer running EnumValuesRewriter after every other one, Mixin included (mixins
 * may target values() calls). Shared arrays come from Java 8's own enum cache. Set up once while the game starts; the
 * Jasione module's saved state decides it, so a toggle applies after a restart.
 */
public final class EnumValues189 implements IClassTransformer {
    private static final Logger LOGGER = LogManager.getLogger("TheLadsCore-1.8.9");
    private static boolean installed, reported;
    private final Predicate<String> isEnum = EnumValuesRewriter.enumCheck(name -> {
        try {
            return Launch.classLoader.getClassBytes(FMLDeobfuscatingRemapper.INSTANCE.unmap(name).replace('/', '.'));
        } catch (IOException e) {
            return null;
        }
    });

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        byte[] rewritten = EnumValuesRewriter.rewrite(bytes, isEnum, false);
        return rewritten != null ? rewritten : bytes;
    }

    @SuppressWarnings("unchecked")
    static void install() {
        if (!ConfigManager.savedEnabled("Jasione", true)) return;
        try {
            Class.forName("sun.misc.SharedSecrets"); // Java 8 only; a newer runtime keeps every call as it is
            // This runs inside a transformer call, which iterates the current list: a new list (old one plus this transformer
            // at the end, after Mixin's) replaces it instead of an edit to the one being iterated.
            Field field = LaunchClassLoader.class.getDeclaredField("transformers");
            field.setAccessible(true);
            List<IClassTransformer> transformers = new ArrayList<>((List<IClassTransformer>) field.get(Launch.classLoader));
            transformers.add(new EnumValues189());
            field.set(Launch.classLoader, transformers);
            MixinEnvironment.getCurrentEnvironment().addTransformerExclusion(EnumValues189.class.getName());
            installed = true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            LOGGER.warn("Jasione could not attach to the class loader; Enum#values() calls stay as they are", e);
        }
    }

    /** The one-line count, once, on the first client tick (the title screen). */
    public static void reportOnce() {
        if (!installed || reported) return;
        reported = true;
        LOGGER.info(EnumValuesRewriter.summary());
    }
}
