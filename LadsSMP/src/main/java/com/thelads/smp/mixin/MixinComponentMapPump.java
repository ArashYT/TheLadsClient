package com.thelads.smp.mixin;

import com.thelads.smp.client.LoadingPump;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.PatchedDataComponentMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Item component reads fill JEI's recipe scans and JER's sorting while the world loads (a 2 s step with this pack); a pump point every 256th call. */
@Mixin(PatchedDataComponentMap.class)
public abstract class MixinComponentMapPump {
    @Inject(method = "get", at = @At("HEAD"), require = 0)
    private void ladssmp$pump(DataComponentType<?> type, CallbackInfoReturnable<Object> cir) {
        LoadingPump.pollFast();
    }
}
