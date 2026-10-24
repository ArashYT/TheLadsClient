package com.thelads.core.v1_8_9.mixin.pvp;

import com.thelads.core.client.PvpVisuals;
import net.minecraft.client.renderer.entity.RenderFish;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityFishHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Render Tweaks, Hide Fishing Line: doRender draws the line only after its first read of the angler is not null; the bobber stays. */
@Mixin(RenderFish.class)
public abstract class RenderFishLineMixin189 {
    @Redirect(method = "doRender(Lnet/minecraft/entity/projectile/EntityFishHook;DDDFF)V", at = @At(value = "FIELD", ordinal = 0,
        target = "Lnet/minecraft/entity/projectile/EntityFishHook;angler:Lnet/minecraft/entity/player/EntityPlayer;"))
    private EntityPlayer ladsHideLine(EntityFishHook hook) {
        return PvpVisuals.hideFishingLine ? null : hook.angler;
    }
}
