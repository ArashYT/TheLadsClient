package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.modules.OldAnimationsModule.Feature;
import com.thelads.core.v1_8_9.feature.OldAnimations189;
import com.thelads.core.v1_8_9.feature.OldAnimations189.Hook;
import com.thelads.core.v1_8_9.feature.RenderTweaks189;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.client.renderer.entity.layers.LayerArmorBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.7 Animations, Red armour on hurt. RendererLivingEntity.renderLayers tints a layer with the body's hurt or death red only when
 * the layer combines textures (setBrightness, the same in OptiFine M5); 1.8's armour layer says no, 1.7 drew armour inside the
 * tinted body pass. Not hurt, the answer changes nothing.
 * Also the armour texture of vanilla armour items, from RenderTweaks189 instead of a String.format (and, under OptiFine, a
 * reflective Forge call) per piece per frame. getArmorResource is Forge's; OptiFine M5 keeps it with the same signature.
 * Without OptiFine, a resource pack's CIT armor texture (type=armor, Cit189) replaces either.
 */
@Mixin(LayerArmorBase.class)
public abstract class LayerArmorBaseMixin {
    @Inject(method = "shouldCombineTextures", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsRedArmour(CallbackInfoReturnable<Boolean> cir) {
        if (!OldAnimations189.active(Feature.RED_ARMOUR)) return;
        cir.setReturnValue(true);
        OldAnimations189.hit(Hook.ARMOUR);
    }

    @Inject(method = "getArmorResource(Lnet/minecraft/entity/Entity;Lnet/minecraft/item/ItemStack;ILjava/lang/String;)Lnet/minecraft/util/ResourceLocation;",
        at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void ladsArmourTexture(Entity entity, ItemStack stack, int slot, String type, CallbackInfoReturnable<ResourceLocation> cir) {
        ResourceLocation texture = RenderTweaks189.armourTexture(stack, slot, type);
        ResourceLocation custom = com.thelads.core.v1_8_9.cit.Cit189.armor(stack, slot, type); // a pack's CIT armor
        if (custom != null) cir.setReturnValue(custom);
        else if (texture != null) cir.setReturnValue(texture);
    }

    @Inject(method = "doRenderLayer", at = @At("HEAD"), require = 0)
    private void lads$instancedArmorLayer(net.minecraft.entity.EntityLivingBase entity, float p_177141_2_, float p_177141_3_, float partialTicks, float p_177141_5_, float p_177141_6_, float p_177141_7_, float scale, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (com.thelads.core.v1_8_9.perf.EntityInstancing189.isAvailable()) {
            com.thelads.core.v1_8_9.perf.EntityInstancing189.recordArmorLayer(entity, 0, null, scale);
        }
    }
}
