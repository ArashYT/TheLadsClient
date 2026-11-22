package com.thelads.core.v1_8_9.perf.mixin;

import com.thelads.core.v1_8_9.perf.LoadPerf189;
import java.io.File;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** QA (-Dlads.qa.loadstats): startup is over, the title screen comes next (LoadPerf189.titleShown). */
@Mixin(Minecraft.class)
public abstract class MinecraftStartStatsMixin189 {
    @Shadow @Final public File mcDataDir;

    @Inject(method = "startGame", at = @At("RETURN"))
    private void lads$loadStats(CallbackInfo ci) {
        LoadPerf189.titleShown(mcDataDir);
    }
}
