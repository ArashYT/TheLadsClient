package com.thelads.core.v26_2.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.thelads.core.perf.BuiltInRender;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lads Kerria on 26.3 (1.8.5). 26.3 already keeps every animation frame on the GPU and interpolates there, drawing each changed
 * sprite into the atlas through a render pass per mip level. A frame change of a plain (not interpolated) animation is a straight
 * texture-to-texture copy through the CommandEncoder instead: no render pass, pipeline or uniforms. Sprites with anisotropic
 * padding and interpolated ones keep the game's own draws. If a backend refuses the copies (GL or Vulkan), they stop for the
 * session with the reason logged and the game's draws take over.
 */
public final class AnimationCopy26 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static boolean failed, announced;
    private static boolean[] drawn = new boolean[64];

    private AnimationCopy26() {}

    /** TextureAtlas.uploadAnimationFrames: true when this replaced it (copies, plus the game's draws for whatever was left). */
    public static boolean uploadFrames(List<SpriteContents.AnimationState> states, List<TextureAtlasSprite> sprites, GpuTexture atlas,
                                       int maxMipLevel, GpuTextureView[] mipViews, Supplier<String> label) {
        if (!BuiltInRender.animations || failed || sprites.size() != states.size()) return false;
        if (!announced) {
            announced = true;
            LOGGER.info("Lads Kerria: animation frames copied texture to texture on {}", RenderSystem.getBackendDescription());
        }
        long start = System.nanoTime();
        if (drawn.length < states.size()) drawn = new boolean[states.size() * 2];
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        int left = 0;
        for (int i = 0; i < states.size(); i++) {
            SpriteContents.AnimationState state = states.get(i);
            drawn[i] = !state.needsToDraw() || copy(encoder, state, sprites.get(i), atlas, maxMipLevel);
            if (!drawn[i]) left++;
        }
        if (left > 0) {
            for (int level = 0; level <= maxMipLevel; level++) {
                try (RenderPass pass = encoder.createRenderPass(label, mipViews[level], Optional.empty())) {
                    RenderSystem.bindDefaultUniforms(pass);
                    for (int i = 0; i < states.size(); i++) {
                        if (drawn[i]) continue;
                        SpriteContents.AnimationState state = states.get(i);
                        state.drawToAtlas(pass, state.getDrawUbo(level));
                    }
                }
            }
        }
        BuiltInRender.animationNanos += System.nanoTime() - start;
        BuiltInRender.animationTicks++;
        return true;
    }

    /** One plain frame change: the current frame's texture copied into the sprite's atlas rectangle on every mip level. */
    private static boolean copy(CommandEncoder encoder, SpriteContents.AnimationState state, TextureAtlasSprite sprite, GpuTexture atlas, int maxMipLevel) {
        AnimationStateLink26 link = (AnimationStateLink26) (Object) state;
        if (failed || link.lads$interpolates() || ((com.thelads.core.v26_2.render.mixin.TextureAtlasSpriteAnimationAccess) sprite).lads$padding() != 0) return false;
        GpuTextureView view = link.lads$currentFrame();
        if (view == null || (view.texture().usage() & GpuTexture.USAGE_COPY_SRC) == 0) return false;
        int width = sprite.contents().width(), height = sprite.contents().height();
        try {
            for (int level = 0; level <= maxMipLevel; level++) {
                int w = width >> level, h = height >> level;
                if (w <= 0 || h <= 0) continue;
                encoder.copyTextureToTexture(view.texture(), atlas, level, sprite.getX() >> level, sprite.getY() >> level, 0, 0, w, h);
            }
        } catch (RuntimeException refused) {
            failed = true;
            LOGGER.warn("Lads Kerria: texture-to-texture animation copies off on {} ({}); the game's animation draws take over",
                RenderSystem.getBackendDescription(), refused.toString());
            return false;
        }
        BuiltInRender.framesCopied++;
        return true;
    }
}
