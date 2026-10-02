package com.thelads.core.v1_21_11.embedded.etf.mixin.mixins;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import com.thelads.core.v1_21_11.embedded.etf.utils.ETFUtils2;


import net.minecraft.client.renderer.rendertype.RenderTypes;
@Mixin(RenderTypes.class)
public abstract class MixinRenderLayer {


    @SuppressWarnings("UnresolvedMixinReference")
    @ModifyVariable(
            method = {
                    "entitySolid",
                    "eyes",
                    "energySwirl",
                    "entitySmoothCutout",
                    "itemEntityTranslucentCull",
                    "entityCutout",
                    "entityCutoutNoCull(Lnet/minecraft/resources/Identifier;Z)Lnet/minecraft/client/renderer/rendertype/RenderType;",
                    "entityCutoutNoCullZOffset(Lnet/minecraft/resources/Identifier;Z)Lnet/minecraft/client/renderer/rendertype/RenderType;",
                    "entityDecal",
                    "entityNoOutline",
                    "entitySmoothCutout",
                    "entityTranslucent(Lnet/minecraft/resources/Identifier;Z)Lnet/minecraft/client/renderer/rendertype/RenderType;",
                    "entityTranslucentEmissive(Lnet/minecraft/resources/Identifier;Z)Lnet/minecraft/client/renderer/rendertype/RenderType;",
                    "armorCutoutNoCull",
                    "entityShadow",
                    "entityTranslucentCullItemTarget",
                    "entityCutoutZOffset(Lnet/minecraft/resources/Identifier;Z)Lnet/minecraft/client/renderer/rendertype/RenderType;",
                    "entityCutoutDissolve",
                    "entitySolidZOffsetForward",
            },
            at = @At(value = "HEAD"),
            index = 0, argsOnly = true)
    private static Identifier etf$mixinAllEntityLayers(Identifier value) {
        return ETFUtils2.getETFVariantNotNullForInjector(value);
    }

}
