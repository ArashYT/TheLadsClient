package com.thelads.smp.features.arise.nb1.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.thelads.smp.features.arise.nb1.CustomItemDespawnFeature;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin target: net.minecraft.world.entity.item.ItemEntity
 * Method: public void tick();
 */
@Mixin(ItemEntity.class)
public abstract class MixinItemEntityDespawn {

    @Inject(method = "tick", at = @At("HEAD"))
    private void ladssmp$customDespawnCheck(CallbackInfo ci) {
        ItemEntity entity = (ItemEntity) (Object) this;
        if (!entity.level().isClientSide() && CustomItemDespawnFeature.isEnabled() && CustomItemDespawnFeature.shouldDespawn(entity)) {
            entity.discard();
        }
    }

    @WrapWithCondition(
        method = "tick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/item/ItemEntity;discard()V", ordinal = 1)
    )
    private boolean ladssmp$preventPrematureVanillaDespawn(ItemEntity entity) {
        if (CustomItemDespawnFeature.isEnabled()) {
            return CustomItemDespawnFeature.shouldDespawn(entity);
        }
        return true;
    }
}
