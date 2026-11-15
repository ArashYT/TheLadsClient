package com.thelads.smp.mixin;

import com.thelads.smp.client.LoadingPump;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Just Enough Resources sorts its loot drops with a comparison that builds display names: a pump point inside that sort. */
@Pseudo
@Mixin(targets = "jeresources.api.drop.LootDrop", remap = false)
public class MixinJerLootSortPump {
    @Inject(method = "compareTo", at = @At("HEAD"), remap = false, require = 0)
    private void ladssmp$pump(CallbackInfoReturnable<Integer> cir) {
        LoadingPump.pollFast();
    }
}
