package com.thelads.smp.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;

/**
 * Smart Backpacks 1.0.16 ships ~93 item icons at 1024x1024 (99% of all item pixels in the pack), which grows the items atlas
 * to 16384x8192 (~537 MB VRAM, the common GPU texture limit) and slows every atlas stitch. Item icons draw at 16-64 px, so
 * shrink any plain item sprite above the cap before stitching. Animated sprites and sprites with extra metadata are untouched.
 * Disable with -Dladssmp.itemTextureCap=0.
 */
@Mixin(SpriteLoader.class)
public class MixinSpriteLoaderTextureCap {
    // ponytail: one global cap for item/ sprites; per-atlas caps if block textures ever need it too
    @Unique private static final int ladssmp$CAP = Integer.getInteger("ladssmp.itemTextureCap", 128);

    @ModifyVariable(method = "stitch", at = @At("HEAD"), argsOnly = true)
    private List<SpriteContents> ladssmp$capItemTextures(List<SpriteContents> sprites) {
        if (ladssmp$CAP <= 0) return sprites;
        List<SpriteContents> out = null;
        int n = 0;
        for (int i = 0; i < sprites.size(); i++) {
            SpriteContents capped = ladssmp$cap(sprites.get(i));
            if (capped == sprites.get(i)) continue;
            if (out == null) out = new ArrayList<>(sprites);
            out.set(i, capped);
            n++;
        }
        if (out == null) return sprites;
        LogUtils.getLogger().info("[Lads SMP] Downscaled {} item textures larger than {}px", n, ladssmp$CAP);
        return out;
    }

    @Unique
    private static SpriteContents ladssmp$cap(SpriteContents s) {
        int w = s.width(), h = s.height();
        if (Math.max(w, h) <= ladssmp$CAP || s.isAnimated() || !s.name().getPath().startsWith("item/")) return s;
        MixinSpriteContentsAccessor acc = (MixinSpriteContentsAccessor) s;
        NativeImage src = acc.ladssmp$originalImage();
        if (!acc.ladssmp$additionalMetadata().isEmpty() || src.getWidth() != w || src.getHeight() != h) return s;
        // multiples of 16, so the atlas keeps up to 4 mip levels (stitch drops mips to the smallest sprite's lowest power of two)
        float k = (float) ladssmp$CAP / Math.max(w, h);
        int nw = Math.max(16, Math.round(w * k / 16) * 16), nh = Math.max(16, Math.round(h * k / 16) * 16);
        NativeImage dst = new NativeImage(src.format(), nw, nh, false);
        src.resizeSubRectTo(0, 0, w, h, dst);
        SpriteContents capped = new SpriteContents(s.name(), new FrameSize(nw, nh), dst);
        s.close();
        return capped;
    }
}
