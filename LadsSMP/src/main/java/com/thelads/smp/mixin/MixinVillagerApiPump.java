package com.thelads.smp.mixin;

import com.thelads.smp.client.LoadingPump;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Villager API writes its 96 village files one village type after the other while the world is being created: a pump point before each. */
@Pseudo
@Mixin(targets = "com.frikinjay.villagerapi.villagerpack.village.VillageGenerator", remap = false)
public class MixinVillagerApiPump {
    @Inject(method = "generateVillageType", at = @At("HEAD"), remap = false, require = 0)
    private static void ladssmp$pump(CallbackInfoReturnable<Boolean> cir) {
        LoadingPump.poll();
    }
}
