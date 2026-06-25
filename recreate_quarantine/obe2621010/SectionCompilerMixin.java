package com.thelads.core.mixin.auto.obe2621010;

import net.minecraft.client.renderer.chunk.SectionCompiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(net.minecraft.client.renderer.chunk.SectionCompiler.class)
public class SectionCompilerMixin {

    @Inject(method = "compile", at = @At("HEAD"), require = 0)
    private void compile(CallbackInfoReturnable<Boolean> cir) {
        // Custom logic here
    }
}
