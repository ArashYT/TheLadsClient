package com.thelads.core.v1_20_1.mixin.visuals;

import com.thelads.core.modules.MobOverlayModule;
import com.thelads.core.v1_20_1.feature.PvpVisuals1201;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Mob Overlay and shulker bullets on 1.20.1: the mobs and bullets the module picks get the glowing outline (EntityTeamColorMixin1201 colors it). */
@Mixin(Minecraft.class)
public abstract class MinecraftGlowMixin1201 {
    @Inject(method = "shouldEntityAppearGlowing(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
    private void ladsMobOverlay(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (MobOverlayModule.active() && MobOverlayModule.outlined(PvpVisuals1201.kind(entity))) cir.setReturnValue(true);
    }
}
