package com.thelads.smp.mixin;

import com.thelads.smp.client.LoadingPump;
import net.minecraft.world.level.block.state.StateHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Block-state property reads fill the model and culling loops of the resource reload (a 5 s step with 350 mods); a pump point every 256th call. */
@Mixin(StateHolder.class)
public abstract class MixinStateHolderPump {
    @Inject(method = "getNullableValue", at = @At("HEAD"), require = 0)
    private void ladssmp$pump(CallbackInfoReturnable<Object> cir) {
        LoadingPump.pollFast();
    }
}
