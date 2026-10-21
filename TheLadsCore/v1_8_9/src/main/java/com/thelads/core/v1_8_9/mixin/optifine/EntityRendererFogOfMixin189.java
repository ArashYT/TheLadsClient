package com.thelads.core.v1_8_9.mixin.optifine;

import com.thelads.core.modules.OptiFine189Module;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * OptiFine Fog and Weather options:
 * - Fog Fancy / Fast / OFF
 * - Fog Start distance scaling
 * - Clear Water underwater fog enhancement
 * - Rain & Snow precipitation gating
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererFogOfMixin189 {
    @Shadow @Final private Minecraft mc;
    @Shadow private float farPlaneDistance;

    @Inject(method = "renderRainSnow", at = @At("HEAD"), cancellable = true)
    private void lads$renderRainSnow(float partialTicks, CallbackInfo ci) {
        if (OptiFine189Module.sOfRain == 3) { // OFF
            ci.cancel();
        }
    }

    @Inject(method = "setupFog", at = @At("HEAD"), cancellable = true)
    private void lads$setupFogHead(int startEnd, float partialTicks, CallbackInfo ci) {
        if (OptiFine189Module.sOfFogType == 2) { // Fog OFF
            GlStateManager.disableFog();
            ci.cancel();
        }
    }

    @Inject(method = "setupFog", at = @At("RETURN"))
    private void lads$setupFogReturn(int startEnd, float partialTicks, CallbackInfo ci) {
        if (OptiFine189Module.sOfFogType == 2) return;

        Entity entity = mc.getRenderViewEntity();
        if (entity == null) return;

        boolean inWater = ActiveRenderInfo.getBlockAtEntityViewpoint(mc.theWorld, entity, partialTicks).getMaterial() == Material.water;

        if (inWater && OptiFine189Module.sOfClearWater) {
            // Clear water: push fog start & end much farther
            GlStateManager.setFogStart(farPlaneDistance * 0.1f);
            GlStateManager.setFogEnd(farPlaneDistance * 0.8f);
            GlStateManager.setFogDensity(0.01f);
            return;
        }

        if (startEnd >= 0) {
            float start = farPlaneDistance * OptiFine189Module.sOfFogStart;
            GlStateManager.setFogStart(start);
            if (OptiFine189Module.sOfFogType == 0) {
                // Fast fog uses GL_EXP
                GlStateManager.setFog(GL11.GL_EXP);
            }
        }
    }
}
