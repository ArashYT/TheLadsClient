package com.thelads.core.perf;

import java.lang.reflect.Method;

/**
 * Built-in render optimizations (1.8.5), always on and with no setting or catalog entry: Lads remakes of Flerovium (CPU back-face
 * culling and LOD for item quads) and Kerria (texture animation frames kept on the GPU, uploads through mapped buffers). Each one
 * stands down for the whole session when its JVM kill switch is false ({@code -Dlads.flerovium=false}, {@code -Dlads.kerria=false})
 * or the real mod is installed, found through Fabric Loader and, under Sinytra Connector or on 1.8.9, Forge's mod lists.
 */
public final class BuiltInRender {
    public static final String FLEROVIUM = "flerovium", KERRIA = "kerria";

    /** Live switches the QA probes flip for before/after runs; the game only reads them. Render thread only. */
    public static boolean items, animations;
    /** Counters for QA and the debug log: item quads written, culled, dropped by LOD; animation frames copied on the GPU, uploaded;
     *  CPU time spent in atlas animation ticks and how many there were. */
    public static long quadsKept, quadsCulled, quadsLod, framesCopied, framesUploaded, animationNanos, animationTicks;

    private static String flerovium, kerria;
    private static boolean decided;

    private BuiltInRender() {}

    /** Why {@code feature} is off this session, or null when it runs. */
    public static synchronized String standDownReason(String feature) {
        if (!decided) {
            flerovium = decide(FLEROVIUM, System.getProperty("lads." + FLEROVIUM), "flerovium");
            kerria = decide(KERRIA, System.getProperty("lads." + KERRIA), "kerria", "kerria_opt", "kerriaopt", "kerria-opt");
            items = flerovium == null;
            animations = kerria == null;
            decided = true;
        }
        return FLEROVIUM.equals(feature) ? flerovium : kerria;
    }

    public static boolean enabled(String feature) { return standDownReason(feature) == null; }

    static String decide(String feature, String killSwitch, String... realModIds) {
        if (killSwitch != null && switchedOff(killSwitch)) return "-Dlads." + feature + "=" + killSwitch;
        for (String id : realModIds) if (modPresent(id)) return "the real mod '" + id + "' is installed";
        return null;
    }

    static boolean switchedOff(String value) {
        String v = value.trim();
        return v.equalsIgnoreCase("false") || v.equals("0") || v.equalsIgnoreCase("off") || v.equalsIgnoreCase("no");
    }

    /** Fabric Loader (which Connector also answers for), Forge's early and late mod lists (1.20.1 Forge), Forge 1.8.9's Loader. */
    static boolean modPresent(String id) {
        Object fabric = call("net.fabricmc.loader.api.FabricLoader", "getInstance", null);
        if (fabric != null && Boolean.TRUE.equals(call(fabric, "isModLoaded", id))) return true;
        Object loading = call("net.minecraftforge.fml.loading.LoadingModList", "get", null);
        if (loading != null && call(loading, "getModFileById", id) != null) return true;
        Object forge = call("net.minecraftforge.fml.ModList", "get", null);
        if (forge != null && Boolean.TRUE.equals(call(forge, "isLoaded", id))) return true;
        return Boolean.TRUE.equals(call("net.minecraftforge.fml.common.Loader", "isModLoaded", id));
    }

    private static Object call(String type, String method, String arg) {
        try {
            Class<?> owner = Class.forName(type, false, BuiltInRender.class.getClassLoader());
            Method m = arg == null ? owner.getMethod(method) : owner.getMethod(method, String.class);
            return arg == null ? m.invoke(null) : m.invoke(null, arg);
        } catch (Throwable absent) { return null; }
    }

    private static Object call(Object target, String method, String arg) {
        try { return target.getClass().getMethod(method, String.class).invoke(target, arg); }
        catch (Throwable absent) { return null; }
    }
}
