package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.Nametags189;
import com.thelads.core.v1_8_9.feature.RenderTweaks189;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.IChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Nametags on living entities (players, armour-stand holograms): renames, your own tag in third person and the sneaking
 * tag's background, which renderName draws itself. OptiFine patches this class: require = 0 leaves vanilla if one moves.
 * The tag's text comes from RenderTweaks189 (the formatted name without vanilla's throwaway hover event), still renamed after.
 */
@Mixin(RendererLivingEntity.class)
public abstract class NametagLivingMixin {
    @Redirect(method = "renderName(Lnet/minecraft/entity/EntityLivingBase;DDD)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/entity/EntityLivingBase;getDisplayName()Lnet/minecraft/util/IChatComponent;"), require = 0)
    private IChatComponent ladsDisplayName(EntityLivingBase entity) {
        return RenderTweaks189.displayName(entity);
    }

    @Redirect(method = "renderName(Lnet/minecraft/entity/EntityLivingBase;DDD)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/util/IChatComponent;getFormattedText()Ljava/lang/String;"), require = 0)
    private String ladsFormattedName(IChatComponent name) {
        return RenderTweaks189.formattedName(name);
    }

    /** The tag text, before either the sneaking or the standing tag (and Essential's icon) measures it. */
    @ModifyVariable(method = "renderName(Lnet/minecraft/entity/EntityLivingBase;DDD)V", at = @At("STORE"), ordinal = 0, require = 0)
    private String ladsRename(String name) {
        return Nametags189.rename(name);
    }

    @ModifyArg(method = "renderName(Lnet/minecraft/entity/EntityLivingBase;DDD)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/WorldRenderer;color(FFFF)Lnet/minecraft/client/renderer/WorldRenderer;"), index = 3, require = 0)
    private float ladsSneakingBackground(float alpha) {
        return Nametags189.background() ? alpha : 0;
    }

    /** As 26.x OwnNametagMixin: only vanilla's own-camera exclusion is lifted; GUI hidden, invisibility and riding still hide it. */
    @Inject(method = "canRenderName(Lnet/minecraft/entity/EntityLivingBase;)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void ladsOwnName(EntityLivingBase entity, CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = Minecraft.getMinecraft();
        if (entity == mc.thePlayer && mc.getRenderViewEntity() == entity && mc.gameSettings.thirdPersonView != 0 && Nametags189.ownNametag()
            && !com.thelads.core.v1_8_9.feature.PaperDoll189.drawing())
            cir.setReturnValue(Minecraft.isGuiEnabled() && !entity.isInvisibleToPlayer(mc.thePlayer) && entity.riddenByEntity == null);
    }
}
