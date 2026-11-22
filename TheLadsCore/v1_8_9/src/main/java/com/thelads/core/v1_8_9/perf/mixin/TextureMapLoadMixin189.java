package com.thelads.core.v1_8_9.perf.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.v1_8_9.perf.LoadPerf189;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The block atlas load (LoadPerf189): its PNGs read and decoded ahead on worker threads, every sprite's mipmaps built at once.
 * Off with -Dlads.parallelatlas=false (then only the load time is logged).
 */
@Mixin(TextureMap.class)
public abstract class TextureMapLoadMixin189 {
    @Shadow @Final private Map<String, TextureAtlasSprite> mapRegisteredSprites;
    @Shadow @Final private String basePath;
    @Shadow(remap = false) private boolean skipFirst;
    @Shadow protected abstract ResourceLocation completeResourceLocation(ResourceLocation location, int mipLevel);

    @Unique private LoadPerf189.Prefetch lads$prefetch;
    @Unique private Set<TextureAtlasSprite> lads$mipmapped;
    @Unique private long lads$start, lads$mipmapNanos;
    @Unique private int lads$mipmapCount;

    @Inject(method = "loadTextureAtlas", at = @At("HEAD"))
    private void lads$readAhead(IResourceManager manager, CallbackInfo ci) {
        lads$done();
        lads$start = System.nanoTime();
        if (!LoadPerf189.ATLAS || skipFirst) return;
        List<ResourceLocation> order = new ArrayList<>(mapRegisteredSprites.size());
        for (TextureAtlasSprite sprite : mapRegisteredSprites.values()) {
            order.add(completeResourceLocation(new ResourceLocation(sprite.getIconName()), 0));
        }
        lads$prefetch = new LoadPerf189.Prefetch(manager, order);
    }

    @WrapOperation(method = "loadTextureAtlas", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/resources/IResourceManager;getResource(Lnet/minecraft/util/ResourceLocation;)Lnet/minecraft/client/resources/IResource;"))
    private IResource lads$getResource(IResourceManager manager, ResourceLocation location, Operation<IResource> original) {
        IResource read = lads$prefetch != null ? lads$prefetch.take(location) : null;
        return read != null ? read : original.call(manager, location);
    }

    @WrapOperation(method = "loadTextureAtlas", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/texture/TextureUtil;readBufferedImage(Ljava/io/InputStream;)Ljava/awt/image/BufferedImage;"))
    private BufferedImage lads$readImage(InputStream stream, Operation<BufferedImage> original) {
        BufferedImage image = lads$prefetch != null ? lads$prefetch.image(stream) : null;
        return image != null ? image : original.call(stream);
    }

    @WrapOperation(method = "loadTextureAtlas", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;generateMipmaps(I)V"))
    private void lads$mipmaps(TextureAtlasSprite sprite, int levels, Operation<Void> original) {
        if (lads$mipmapped == null) {
            long t = System.nanoTime();
            lads$mipmapped = LoadPerf189.ATLAS && !skipFirst ? LoadPerf189.mipmapAll(mapRegisteredSprites.values(), levels)
                : java.util.Collections.<TextureAtlasSprite>emptySet();
            lads$mipmapCount = lads$mipmapped.size();
            lads$mipmapNanos = System.nanoTime() - t;
        }
        if (!lads$mipmapped.remove(sprite)) original.call(sprite, levels);
    }

    @Inject(method = "loadTextureAtlas", at = @At("RETURN"))
    private void lads$report(IResourceManager manager, CallbackInfo ci) {
        if (skipFirst) return;
        LoadPerf189.logAtlas(basePath, mapRegisteredSprites.size(), lads$prefetch != null ? lads$prefetch.used : 0, lads$mipmapCount,
            lads$mipmapNanos, System.nanoTime() - lads$start);
        lads$done();
    }

    @Unique
    private void lads$done() {
        if (lads$prefetch != null) lads$prefetch.close();
        lads$prefetch = null;
        lads$mipmapped = null;
        lads$mipmapCount = 0;
        lads$mipmapNanos = 0;
    }
}
