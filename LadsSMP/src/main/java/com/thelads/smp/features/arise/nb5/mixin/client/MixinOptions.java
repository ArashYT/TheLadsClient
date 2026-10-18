package com.thelads.smp.features.arise.nb5.mixin.client;

import com.thelads.smp.features.arise.nb5.client.ParkourKeyMappings;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into Options to register NB5 parkour keybindings into the controls list.
 */
@Mixin(Options.class)
public abstract class MixinOptions {

    @Shadow
    @Final
    @Mutable
    public KeyMapping[] keyMappings;

    @Inject(method = "load", at = @At("RETURN"))
    private void ladssmp$appendParkourKeyMappings(CallbackInfo ci) {
        this.keyMappings = ParkourKeyMappings.appendKeys(this.keyMappings);
    }
}
