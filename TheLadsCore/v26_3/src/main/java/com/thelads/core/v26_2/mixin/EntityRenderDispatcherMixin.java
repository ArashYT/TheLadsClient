package com.thelads.core.v26_2.mixin;

import com.thelads.core.client.hypixel.CrowdPlayerHider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {
    @Inject(method = "shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDDF)Z",
            at = @At("HEAD"), cancellable = true, require = 0)
    private <E extends Entity> void lads$cullPitCrowdPlayer(E entity, Frustum frustum, double camX, double camY, double camZ, float partialTick, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof Avatar && CrowdPlayerHider.shouldHidePitPlayer(entity == Minecraft.getInstance().player, entity.getX(), entity.getY(), entity.getZ())) {
            cir.setReturnValue(false);
        }
    }
}
