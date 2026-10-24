package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.client.cosmetics.wearables.CosmeticItem;
import com.thelads.core.client.cosmetics.wearables.CosmeticLibrary;
import com.thelads.core.client.cosmetics.wearables.CosmeticSlot;
import com.thelads.core.client.cosmetics.wearables.CosmeticsManager;
import com.thelads.core.modules.CustomFovModule;
import com.thelads.core.v1_8_9.cosmetics.wearables.CosmeticsLayer189;
import com.thelads.core.v1_8_9.feature.CustomFov189;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/** Custom FOV and Lads wearable capes on 1.8.9. */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
    @ModifyConstant(method = "getFovModifier", constant = @Constant(floatValue = 1.1F), require = 1)
    private float ladsFlying(float change) {
        return CustomFov189.scaled(change, CustomFovModule.FLYING);
    }

    @Redirect(method = "getFovModifier", require = 1,
        at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/ai/attributes/IAttributeInstance;getAttributeValue()D"))
    private double ladsSpeed(IAttributeInstance attribute) {
        return CustomFov189.speed(attribute);
    }

    /** The bow's pull narrows the FOV by up to this much. */
    @ModifyConstant(method = "getFovModifier", constant = @Constant(floatValue = 0.15F), require = 1)
    private float ladsBow(float narrowing) {
        return narrowing * (float) CustomFov189.share(CustomFovModule.BOW);
    }

    @Inject(method = "getLocationCape", at = @At("HEAD"), cancellable = true)
    private void ladsEquippedCape(CallbackInfoReturnable<ResourceLocation> cir) {
        AbstractClientPlayer player = (AbstractClientPlayer) (Object) this;
        CosmeticsManager manager = CosmeticsManager.get();
        UUID uuid = player.getUniqueID();
        if (!manager.shouldRender(uuid, CosmeticSlot.CAPE)) return;
        String capeId = manager.getEquippedCape(uuid);
        if (capeId != null && !capeId.isEmpty() && !"none".equals(capeId)) {
            CosmeticItem item = CosmeticLibrary.get(capeId);
            if (item != null && item.texturePath() != null) {
                cir.setReturnValue(CosmeticsLayer189.getResource(item.texturePath()));
            }
        }
    }
}
