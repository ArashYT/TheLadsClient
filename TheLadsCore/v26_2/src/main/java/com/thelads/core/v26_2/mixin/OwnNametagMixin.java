package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.NativeQualityOfLife;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntityRenderer.class)
public class OwnNametagMixin {
    // Remove only vanilla's local-camera exclusion. All other distance, team,
    // sneaking, invisibility and hidden-HUD checks remain in vanilla's method.
    @Redirect(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getCameraEntity()Lnet/minecraft/world/entity/Entity;"), require = 1)
    private Entity lads$ownName(Minecraft minecraft, LivingEntity entity, double distanceSquared) {
        Entity camera = minecraft.getCameraEntity();
        if (entity == minecraft.player && camera == minecraft.player
            && !minecraft.options.getCameraType().isFirstPerson()
            && NativeQualityOfLife.enabled("Nametags")
            && NativeQualityOfLife.bool("Nametags", "Show Own Nametag in Third Person", false))
            return null;
        return camera;
    }
}
