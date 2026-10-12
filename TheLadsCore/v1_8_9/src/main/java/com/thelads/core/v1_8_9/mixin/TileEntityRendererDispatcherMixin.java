package com.thelads.core.v1_8_9.mixin;

import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TileEntityRendererDispatcher.class)
public abstract class TileEntityRendererDispatcherMixin {
    @Unique private Class<?> ladsLastClass;
    @Unique private TileEntitySpecialRenderer<?> ladsLastRenderer;

    @Inject(method = "getSpecialRendererByClass", at = @At("HEAD"), cancellable = true)
    private void ladsFastGetSpecialRendererByClass(Class<? extends TileEntity> teClass, CallbackInfoReturnable<TileEntitySpecialRenderer<?>> cir) {
        if (teClass == this.ladsLastClass && this.ladsLastRenderer != null) {
            cir.setReturnValue(this.ladsLastRenderer);
        }
    }

    @Inject(method = "getSpecialRendererByClass", at = @At("RETURN"))
    private void ladsCacheSpecialRendererByClass(Class<? extends TileEntity> teClass, CallbackInfoReturnable<TileEntitySpecialRenderer<?>> cir) {
        this.ladsLastClass = teClass;
        this.ladsLastRenderer = cir.getReturnValue();
    }
}
