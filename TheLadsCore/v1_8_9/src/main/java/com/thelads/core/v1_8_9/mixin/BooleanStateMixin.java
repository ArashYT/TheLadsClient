package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.GlState189;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** GlStateManager's on/off switch for one GL capability: GlState189 reads blend, depth and alpha from it instead of asking the driver. */
@Mixin(targets = "net.minecraft.client.renderer.GlStateManager$BooleanState")
@Implements(@Interface(iface = GlState189.Switch.class, prefix = "lads$"))
public abstract class BooleanStateMixin {
    /** The annotation processor cannot map a member of this nested class, so the production (SRG) name is given as an alias. */
    @Shadow(aliases = "field_179201_b") private boolean currentState;

    @Inject(method = "<init>", at = @At("RETURN"), require = 1)
    private void ladsTrack(int capability, CallbackInfo ci) {
        GlState189.track(capability, (GlState189.Switch) this);
    }

    public boolean lads$on() { return currentState; }
}
