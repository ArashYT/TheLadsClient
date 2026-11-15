package com.thelads.smp.mixin;

import com.thelads.smp.client.LoadingPump;
import net.minecraft.client.Minecraft;
import net.minecraft.util.thread.BlockableEventLoop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.BooleanSupplier;

/**
 * Pump points on the client thread: after every task it runs (resource-reload apply steps and world-load steps are such tasks), and
 * while it waits in managedBlock for something another thread loads (the data packs of a new world), which vanilla does without drawing.
 */
@Mixin(BlockableEventLoop.class)
public abstract class MixinBlockableEventLoopPump {
    @Inject(method = "pollTask", at = @At("RETURN"), require = 0)
    private void ladssmp$pumpAfterTask(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && (Object) this == Minecraft.getInstance()) LoadingPump.poll();
    }

    @Inject(method = "managedBlock", at = @At("HEAD"), require = 0)
    private void ladssmp$blockStart(BooleanSupplier condition, CallbackInfo ci) {
        if ((Object) this == Minecraft.getInstance()) LoadingPump.blockStart();
    }

    @Inject(method = "managedBlock", at = @At("RETURN"), require = 0)
    private void ladssmp$blockEnd(BooleanSupplier condition, CallbackInfo ci) {
        if ((Object) this == Minecraft.getInstance()) LoadingPump.blockEnd();
    }

    @Inject(method = "waitForTasks", at = @At("HEAD"), require = 0)
    private void ladssmp$pumpWhileWaiting(CallbackInfo ci) {
        if ((Object) this == Minecraft.getInstance()) LoadingPump.poll();
    }
}
