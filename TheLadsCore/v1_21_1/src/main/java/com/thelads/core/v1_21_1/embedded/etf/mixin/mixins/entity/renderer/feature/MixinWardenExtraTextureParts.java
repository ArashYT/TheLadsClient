package com.thelads.core.v1_21_1.embedded.etf.mixin.mixins.entity.renderer.feature;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.model.WardenModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

import net.minecraft.client.model.WardenModel;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.client.renderer.entity.layers.WardenEmissiveLayer;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import com.thelads.core.v1_21_1.embedded.etf.ETF;
import com.thelads.core.v1_21_1.embedded.etf.utils.ETFUtils2;

@Mixin(WardenEmissiveLayer.class)
public abstract class MixinWardenExtraTextureParts<T extends Warden, M extends WardenModel<T>> extends RenderLayer<T, M> {
    @SuppressWarnings("unused")
    public MixinWardenExtraTextureParts() {
        super(null);
    }

        @Unique
    private static final ResourceLocation VANILLA_TEXTURE = ETFUtils2.res("textures/entity/warden/warden.png");
    @Shadow
    @Final
    private ResourceLocation texture;




    @Shadow
    protected abstract void resetDrawForAllParts();

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/monster/warden/Warden;FFFFFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/layers/WardenEmissiveLayer;onlyDrawSelectedParts()V",
                    shift = At.Shift.AFTER))
    private void etf$preventHiding(CallbackInfo ci) {
        if (ETF.config().getConfig().enableFullBodyWardenTextures && !VANILLA_TEXTURE.equals(texture)) {
            resetDrawForAllParts();
        }
    }
}





