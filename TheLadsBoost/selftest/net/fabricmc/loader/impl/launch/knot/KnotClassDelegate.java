package net.fabricmc.loader.impl.launch.knot;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/** FAKE with the same member names/descriptors as fabric-loader 0.19.5 (erased generics). Test only. */
public final class KnotClassDelegate {
    public static final AtomicInteger ORIG = new AtomicInteger();
    private final ClassLoader classLoader;
    private Object mixinTransformer;
    private boolean transformInitialized;

    public KnotClassDelegate(ClassLoader cl) { classLoader = cl; }

    public void initializeTransformers() {
        if (transformInitialized) throw new IllegalStateException("twice");
        mixinTransformer = new Object();
        transformInitialized = true;
    }

    /** stands in for tryLoadClass, the only caller in the real class */
    public byte[] tryLoad(String name, boolean allowFromParent) { return getPostMixinClassByteArray(name, allowFromParent); }

    private byte[] getPostMixinClassByteArray(String name, boolean allowFromParent) {
        ORIG.incrementAndGet();
        org.spongepowered.asm.mixin.Mixins.visitAll();
        try { Thread.sleep(2); } catch (InterruptedException e) { throw new RuntimeException(e); }
        if (name.startsWith("missing.")) return null;
        boolean t = transformInitialized && canTransformClass(name);
        return ("bytes:" + name + (t ? ":T" : ":R")).getBytes(StandardCharsets.UTF_8);
    }

    private static boolean canTransformClass(String name) { return !name.startsWith("net.fabricmc.loader."); }
}
