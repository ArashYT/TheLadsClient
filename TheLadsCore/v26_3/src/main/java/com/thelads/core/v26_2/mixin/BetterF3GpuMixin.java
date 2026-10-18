package com.thelads.core.v26_2.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.v26_2.feature.NativeBetterF3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntryList;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Better F3's GPU Utilization line: the game measures its GPU load only for its own debug entry, so also while that line shows. */
@Mixin(Minecraft.class)
public class BetterF3GpuMixin {
    @WrapOperation(method = "renderFrame", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/components/debug/DebugScreenEntryList;isCurrentlyEnabled(Lnet/minecraft/resources/Identifier;)Z"), require = 0)
    private boolean lads$betterF3Gpu(DebugScreenEntryList entries, Identifier id, Operation<Boolean> original) {
        return original.call(entries, id) || id.equals(DebugScreenEntries.GPU_UTILIZATION) && NativeBetterF3.measuresGpu();
    }
}
