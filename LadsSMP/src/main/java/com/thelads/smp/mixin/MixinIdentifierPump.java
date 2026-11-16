package com.thelads.smp.mixin;

import com.thelads.smp.client.LoadingPump;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Identifiers are created by the million while mods load, bake and index; a cheap pump point (every 256th call looks at the clock) inside long mod loops. */
@Mixin(Identifier.class)
public abstract class MixinIdentifierPump {
    @Inject(method = "<init>", at = @At("RETURN"), require = 0)
    private void ladssmp$pump(CallbackInfo ci) {
        LoadingPump.pollFast();
    }
}
