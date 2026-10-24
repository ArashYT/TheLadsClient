package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.modules.ViewModelModule;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ViewModel on 1.8.9: right after renderItemInFirstPerson pushes the hand's matrix (before the item, map or bare arm is placed), the
 * offset, then the rotation and scale around vanilla's item position. 1.8.9 has only the right hand. Hand FOV: Zoom189's FOV event.
 */
@Mixin(ItemRenderer.class)
public abstract class ViewModelMixin189 {
    @Inject(method = "renderItemInFirstPerson", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;pushMatrix()V",
        ordinal = 0, shift = At.Shift.AFTER), require = 1)
    private void lads$viewModel(float partialTicks, CallbackInfo ci) {
        ViewModelModule vm = ViewModelModule.get();
        if (!vm.posed()) return;
        GlStateManager.translate((float) vm.x.getValue(), (float) vm.y.getValue(), (float) vm.z.getValue());
        GlStateManager.translate(ViewModelModule.PIVOT_X, ViewModelModule.PIVOT_Y, ViewModelModule.PIVOT_Z);
        GlStateManager.rotate((float) vm.rotY.getValue(), 0, 1, 0);
        GlStateManager.rotate((float) vm.rotX.getValue(), 1, 0, 0);
        GlStateManager.rotate((float) vm.rotZ.getValue(), 0, 0, 1);
        float scale = (float) (vm.scale.getValue() / 100);
        GlStateManager.scale(scale, scale, scale);
        GlStateManager.translate(-ViewModelModule.PIVOT_X, -ViewModelModule.PIVOT_Y, -ViewModelModule.PIVOT_Z);
    }
}
