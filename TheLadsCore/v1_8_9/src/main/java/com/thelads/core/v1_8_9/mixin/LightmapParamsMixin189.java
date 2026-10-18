package com.thelads.core.v1_8_9.mixin;

import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 1.9.13, without OptiFine (EarlyPlugin189): enableLightmap, called ~8 times a frame (each terrain layer, entities, particles, the
 * hand, weather), sends the lightmap texture the same four parameters (linear filtering, clamp) every time. A texture keeps its
 * parameters, so they go out once per lightmap texture: 32 of ~1,100 GL calls a frame. (OptiFine's shaders switch lightmap
 * parameters on their own paths, so with OptiFine every call still goes out.)
 */
@Mixin(EntityRenderer.class)
public abstract class LightmapParamsMixin189 {
    @Shadow private DynamicTexture lightmapTexture;
    @Unique private int lads$paramsFor = -1, lads$paramsSent;

    @Redirect(method = "enableLightmap", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glTexParameteri(III)V", remap = false), require = 0)
    private void lads$lightmapParams(int target, int pname, int param) {
        int id = lightmapTexture.getGlTextureId();
        if (id != lads$paramsFor) {
            lads$paramsFor = id;
            lads$paramsSent = 0;
        }
        if (lads$paramsSent >= 4) return;
        lads$paramsSent++;
        GL11.glTexParameteri(target, pname, param);
    }
}
