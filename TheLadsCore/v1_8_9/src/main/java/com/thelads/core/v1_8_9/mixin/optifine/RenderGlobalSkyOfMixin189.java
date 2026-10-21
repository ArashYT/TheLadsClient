package com.thelads.core.v1_8_9.mixin.optifine;

import com.thelads.core.modules.OptiFine189Module;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * OptiFine Sky, Stars, Sun &amp; Moon, and Chunk Updates controls:
 * - Sky dome gating
 * - Star field gating
 * - Celestial bodies gating
 * - Chunk Updates per frame limiting
 */
@Mixin(RenderGlobal.class)
public abstract class RenderGlobalSkyOfMixin189 {
    @Shadow private int starGLCallList;
    @Shadow @Final private static ResourceLocation locationSunPng;
    @Shadow @Final private static ResourceLocation locationMoonPhasesPng;
    @Unique private int lads$chunkUpdatesCount;

    @Inject(method = "renderSky(FI)V", at = @At("HEAD"), cancellable = true)
    private void lads$renderSky(float partialTicks, int pass, CallbackInfo ci) {
        if (!OptiFine189Module.sOfSky) {
            ci.cancel();
        }
    }

    @Redirect(method = "renderSky(FI)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;callList(I)V"))
    private void lads$stars(int list) {
        if (list == this.starGLCallList) {
            if (OptiFine189Module.sOfStars) {
                GlStateManager.callList(list);
            }
            return;
        }
        GlStateManager.callList(list);
    }

    @Redirect(method = "renderSky(FI)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/TextureManager;bindTexture(Lnet/minecraft/util/ResourceLocation;)V"))
    private void lads$sunMoon(TextureManager manager, ResourceLocation location) {
        if (!OptiFine189Module.sOfSunMoon) {
            if (location.equals(locationSunPng) || location.equals(locationMoonPhasesPng)) {
                return;
            }
        }
        manager.bindTexture(location);
    }

    @Inject(method = "updateChunks", at = @At("HEAD"), require = 0)
    private void lads$resetChunkUpdates(long finishTimeNano, CallbackInfo ci) {
        lads$chunkUpdatesCount = 0;
    }

    @Redirect(method = "updateChunks", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/chunk/ChunkRenderDispatcher;updateChunkLater(Lnet/minecraft/client/renderer/chunk/RenderChunk;)Z"), require = 0)
    private boolean lads$limitChunkUpdates(ChunkRenderDispatcher dispatcher, RenderChunk chunk) {
        int max = OptiFine189Module.sOfChunkUpdates;
        if (lads$chunkUpdatesCount >= max) {
            return false;
        }
        boolean ok = dispatcher.updateChunkLater(chunk);
        if (ok) {
            lads$chunkUpdatesCount++;
        }
        return ok;
    }
}
