package com.thelads.core.v1_8_9.mixin.visuals;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.modules.MobOverlayModule;
import com.thelads.core.v1_8_9.feature.PvpVisuals189;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.client.shader.ShaderGroup;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mob Overlay on 1.8.9: 1.8.9's only see-through outline is the spectator one (the entity_outline shader, players only, while the
 * spectator outline key is held). With the module on, that pass runs every frame; it outlines the mobs the module picks, and players
 * only when vanilla would. RendererLivingEntityVisualsMixin189 gives each mob its color. The pass needs the main framebuffer: with Fast
 * Render (Ionium's or OptiFine's) it stays off and the mobs are tinted instead (RendererLivingEntityVisualsMixin189).
 */
@Mixin(RenderGlobal.class)
public abstract class RenderGlobalOutlineMixin189 {
    @Shadow private Framebuffer entityOutlineFramebuffer;
    @Shadow private ShaderGroup entityOutlineShader;

    @Inject(method = "isRenderEntityOutlines", at = @At("HEAD"), cancellable = true)
    private void ladsMobOutlines(CallbackInfoReturnable<Boolean> cir) {
        if (MobOverlayModule.active() && OpenGlHelper.isFramebufferEnabled() && entityOutlineFramebuffer != null && entityOutlineShader != null
            && Minecraft.getMinecraft().thePlayer != null) cir.setReturnValue(true);
    }

    @WrapOperation(method = "renderEntities", constant = @Constant(classValue = EntityPlayer.class))
    private boolean ladsOutlined(Object entity, Operation<Boolean> original) {
        if (original.call(entity)) {
            Minecraft mc = Minecraft.getMinecraft();
            return mc.thePlayer.isSpectator() && mc.gameSettings.keyBindSpectatorOutlines.isKeyDown(); // vanilla's own player outlines
        }
        return MobOverlayModule.outlined(PvpVisuals189.mobKind((Entity) entity));
    }
}
