package com.thelads.core.v26_2.qol.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.v26_2.qol.Qol26;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Crash Catcher: runTick's call to tick(), the game tick only (never a frame). */
@Mixin(Minecraft.class)
abstract class MinecraftQolMixin26 {
    /** require = 0: a pack mod that rewrites runTick cannot stop the game from starting; Qol26 then reports Crash Catcher unavailable. */
    @WrapOperation(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;tick()V"), require = 0)
    private void lads$catchCrash(Minecraft mc, Operation<Void> original) {
        Qol26.tickHooked();
        try {
            original.call(mc);
        } catch (RuntimeException error) {
            if (!Qol26.crashed(mc, error)) throw error;
        }
    }
}
