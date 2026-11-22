package com.thelads.core.v1_8_9.perf;

import java.util.List;
import java.util.Set;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * theladscore.v1_8_9.perf189.mixins.json: faster loading (LoadPerf189). The atlas hooks only without OptiFine, which has its own
 * TextureMap and mipmaps. Kill switches: -Dlads.parallelatlas=false, -Dlads.asyncskins=false, -Dlads.dedupe189=false (also quad interning), -Dlads.quadintern=false. No Minecraft class may load here.
 */
public final class LoadPerfPlugin189 implements IMixinConfigPlugin {
    static final boolean OPTIFINE = LoadPerfPlugin189.class.getClassLoader().getResource("optifine/OptiFineForgeTweaker.class") != null;
    static final boolean ATLAS = !off("lads.parallelatlas") && !OPTIFINE;
    static final boolean SKINS = !off("lads.asyncskins");
    static final boolean DEDUPE = !off("lads.dedupe189");
    /** Quad interning: not with OptiFine, which patches BakedQuad and its vertex data itself. */
    static final boolean QUADS = DEDUPE && !OPTIFINE && !off("lads.quadintern");
    /** TextureUtilMipmapMixin189 applied: TextureUtil.generateMipmapData may run on several threads at once. */
    static volatile boolean mipmapThreadSafe;

    static boolean off(String key) {
        String v = System.getProperty(key);
        return v != null && ("false".equalsIgnoreCase(v) || "0".equals(v) || "off".equalsIgnoreCase(v) || "no".equalsIgnoreCase(v));
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith("TextureMapLoadMixin189")) return !OPTIFINE; // times every load; works only with ATLAS
        if (mixinClassName.endsWith("TextureUtilMipmapMixin189")) return ATLAS;
        if (mixinClassName.endsWith("ThreadDownloadImageDataMixin189")) return SKINS;
        if (mixinClassName.endsWith("ModelManagerDedupeMixin189")) return DEDUPE;
        if (mixinClassName.endsWith("BakedQuadInternMixin189")) return QUADS; // also UnpackedBakedQuadInternMixin189
        return true;
    }

    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // Parallel mipmaps only once TextureUtil's shared mipmap buffer is out of the way.
        if (mixinClassName.endsWith("TextureUtilMipmapMixin189")) mipmapThreadSafe = true;
    }
}
