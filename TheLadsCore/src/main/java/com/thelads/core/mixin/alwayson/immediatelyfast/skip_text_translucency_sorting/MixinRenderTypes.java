/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.renderer.rendertype.RenderSetup$RenderSetupBuilder
 *  net.minecraft.client.renderer.rendertype.RenderTypes
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Redirect
 */
package com.thelads.core.mixin.alwayson.immediatelyfast.skip_text_translucency_sorting;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value={RenderTypes.class}, priority=500)
public abstract class MixinRenderTypes {
    @Redirect(method={"lambda$static$22", "lambda$static$23", "lambda$static$25"}, at=@At(value="INVOKE", target="Lnet/minecraft/client/renderer/rendertype/RenderSetup$RenderSetupBuilder;sortOnUpload()Lnet/minecraft/client/renderer/rendertype/RenderSetup$RenderSetupBuilder;"))
    private static RenderSetup.RenderSetupBuilder disableTranslucencySorting(RenderSetup.RenderSetupBuilder instance) {
        return instance;
    }
}

