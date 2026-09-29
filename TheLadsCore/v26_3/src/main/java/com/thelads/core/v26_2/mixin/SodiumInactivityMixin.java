package com.thelads.core.v26_2.mixin;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo
@Mixin(targets="net.caffeinemc.mods.sodium.client.config.builder.OptionGroupBuilderImpl",remap=false)
public abstract class SodiumInactivityMixin {
    @Inject(method="addOption",at=@At("HEAD"),cancellable=true,require=1,remap=false)
    private void ladsRemoveIdleOption(@Coerce Object option,CallbackInfoReturnable<Object> ci){
        if(option instanceof SodiumOptionAccessor access && access.ladsOptionId().getPath().equals("performance.inactivity_fps_limit"))ci.setReturnValue(this);
    }
}
