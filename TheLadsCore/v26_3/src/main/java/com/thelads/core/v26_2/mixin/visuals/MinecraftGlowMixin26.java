package com.thelads.core.v26_2.mixin.visuals;

import com.thelads.core.modules.MobOverlayModule;
import com.thelads.core.v26_2.feature.PvpVisuals26;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Mob Overlay and shulker bullets on 26.3: the mobs and bullets the module picks get the glowing outline (EntityTeamColorMixin26 colors it). */
@Mixin(Minecraft.class)
public abstract class MinecraftGlowMixin26 {
    @Inject(method = "shouldEntityAppearGlowing(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
    private void ladsMobOverlay(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (MobOverlayModule.active() && MobOverlayModule.outlined(PvpVisuals26.kind(entity))) cir.setReturnValue(true);
    }
}
