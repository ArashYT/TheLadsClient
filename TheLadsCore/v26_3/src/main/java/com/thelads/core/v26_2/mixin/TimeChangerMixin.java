package com.thelads.core.v26_2.mixin;

import com.thelads.core.modules.TimeChangerModule;
import net.minecraft.client.ClientClockManager;
import net.minecraft.world.clock.ClockInstance;
import net.minecraft.world.clock.ClockManager;
import net.minecraft.world.timeline.AttributeTrackSampler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Time Changer on 26.3: the timelines behind the sun and moon angle, sky colour and daylight sample the client's clocks at the chosen
 * time on the real day. Only samplers built on the client's clocks (ClientClockManager): the server's environment, the stored clock
 * time and every other reader (the Time and Day HUD lines, Better F3) keep the real time, as on 1.8.9 and 1.20.1.
 */
@Mixin(AttributeTrackSampler.class)
public abstract class TimeChangerMixin {
    @Shadow @Final private ClockManager clockManager;

    @Redirect(method = "applyTimeBased", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/clock/ClockInstance;totalTicks()J"), require = 1)
    private long lads$timeChanger(ClockInstance clock) {
        long real = clock.totalTicks();
        if (!(clockManager instanceof ClientClockManager)) return real;
        TimeChangerModule changer = TimeChangerModule.get();
        return changer != null && changer.isEnabled() ? changer.time(real) : real;
    }
}
