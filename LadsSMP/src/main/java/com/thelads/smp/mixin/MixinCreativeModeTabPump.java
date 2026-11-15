package com.thelads.smp.mixin;

import com.thelads.smp.client.LoadingPump;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The client builds all ~60 creative tabs inside the login packet (TCD Commons, JEI); a pump point before each tab. */
@Mixin(CreativeModeTab.class)
public abstract class MixinCreativeModeTabPump {
    @Inject(method = "buildContents", at = @At("HEAD"), require = 0)
    private void ladssmp$pump(CreativeModeTab.ItemDisplayParameters parameters, CallbackInfo ci) {
        LoadingPump.poll();
    }
}
