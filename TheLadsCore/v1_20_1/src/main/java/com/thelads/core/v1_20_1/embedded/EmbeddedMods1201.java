package com.thelads.core.v1_20_1.embedded;

import com.thelads.core.v1_20_1.compat.ModPresence1201;
import java.lang.reflect.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Upstream mods rebuilt inside Core with no Lads module, one package each under {@code ...v1_20_1.embedded.<name>}. Each
 * stands down while its original jar is installed (plain Fabric jar, or Forge jar under Connector): its mixin config's plugin
 * ({@link com.thelads.core.v1_20_1.perf.StandDownMixinPlugin1201}) applies none of its mixins, and {@link #clientInit()} skips
 * its setup, so a copy a pack ships is never run twice.
 *
 * <p>An embedded mod provides a class with a public static {@code init()} (named in {@link #ENTRIES}); the call goes through
 * reflection so one jar can carry any subset of them. The log lines "Lads embedded NAME: active/standing down" are what the QA
 * runs look for.
 */
public final class EmbeddedMods1201 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore-Embedded");

    /** name, ids of the original mod (Fabric id and Forge id), class with {@code public static void init()}. */
    private record Entry(String name, String[] originals, String initClass) {}

    private static final String BASE = "com.thelads.core.v1_20_1.embedded.";
    private static final Entry[] ENTRIES = {
        new Entry("entityculling", new String[] {"entityculling"}, BASE + "entityculling.EntityCullingInit1201"),
        new Entry("lazyai", new String[] {"lazy_ai_pixelindiedev"}, BASE + "lazyai.LazyAiInit1201"),
        new Entry("ksyxis", new String[] {"ksyxis"}, BASE + "ksyxis.KsyxisInit1201"),
        new Entry("serverpingerfixer", new String[] {"serverpingerfixer"}, BASE + "serverpingerfixer.ServerPingerFixerInit1201"),
        new Entry("fastipping", new String[] {"fastipping"}, BASE + "fastipping.FastIpPingInit1201"),
        new Entry("fixbookgui", new String[] {"fixbookgui"}, BASE + "fixbookgui.FixBookGuiInit1201"),
        new Entry("hoveringhotbar", new String[] {"hoveringhotbar"}, BASE + "hoveringhotbar.HoveringHotbarInit1201"),
        new Entry("quickpack", new String[] {"quick-pack", "quickpack"}, BASE + "quickpack.QuickPackInit1201"),
        new Entry("tooltips", new String[] {"tooltipstxf"}, BASE + "tooltips.ExtraTooltipsInit1201"),
        new Entry("playtime", new String[] {"worldplaytimereborn", "worldplaytime"}, BASE + "playtime.PlayTimeInit1201"),
        new Entry("capes", new String[] {"capes"}, BASE + "capes.CapesInit1201"),
        new Entry("cushions", new String[] {"optimizedcushions"}, BASE + "cushions.CushionsInit1201"),
        new Entry("controlling", new String[] {"controlling"}, BASE + "controlling.ControllingInit1201"),
        new Entry("immediatelyfast", new String[] {"immediatelyfast"}, BASE + "immediatelyfast.ImmediatelyFast"),
        new Entry("nbtac", new String[] {"nbtac"}, BASE + "nbtac.NBTacFabric"),
        new Entry("etf", new String[] {"entity_texture_features"}, BASE + "etf.ETFInit"),
        // EMF builds on ETF, so it also stays off on top of an installed ETF jar.
        new Entry("emf", new String[] {"entity_model_features", "entity_texture_features"}, BASE + "emf.EMFInit"),
    };

    private EmbeddedMods1201() {}

    /** True while the original mod is not installed, i.e. the embedded copy is the one that runs. */
    public static boolean active(String name) {
        for (Entry entry : ENTRIES) {
            if (entry.name().equals(name)) return !ModPresence1201.loaded(entry.originals());
        }
        return false;
    }

    /** Called once from the performance registration, at client initialization. */
    public static void clientInit() {
        for (Entry entry : ENTRIES) {
            String installed = null;
            for (String id : entry.originals()) {
                if (ModPresence1201.loaded(id)) {
                    installed = id;
                    break;
                }
            }
            if (installed != null) {
                LOGGER.info("Lads embedded {}: standing down, '{}' is installed ({})", entry.name(), installed, ModPresence1201.where(installed));
                continue;
            }
            Method init;
            try {
                init = Class.forName(entry.initClass()).getMethod("init");
            } catch (ClassNotFoundException absent) {
                LOGGER.info("Lads embedded {}: not part of this 1.20.1 build (the original mod is not installed either, so nothing provides it)", entry.name());
                continue;
            } catch (ReflectiveOperationException | LinkageError broken) {
                LOGGER.error("Lads embedded {}: init class is unusable", entry.name(), broken);
                continue;
            }
            try {
                init.invoke(null);
                LOGGER.info("Lads embedded {}: active", entry.name());
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                Throwable cause = failure instanceof java.lang.reflect.InvocationTargetException wrapped && wrapped.getCause() != null ? wrapped.getCause() : failure;
                LOGGER.error("Lads embedded {}: initialization failed, the feature is off", entry.name(), cause);
            }
        }
    }
}
