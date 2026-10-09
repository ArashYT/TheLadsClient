package com.thelads.core.v1_20_1.hud.chat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.telemetry.ClientTelemetryManager;
import net.minecraft.client.telemetry.TelemetryEventSender;
import net.minecraft.client.telemetry.TelemetryLogManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Privacy: Minecraft's telemetry is off. No session is opened and no event is sent or written to logs/telemetry. */
@Mixin(ClientTelemetryManager.class)
public abstract class PrivacyMixin {
    @Inject(method = "createEventSender", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$disableTelemetry(CallbackInfoReturnable<TelemetryEventSender> cir) {
        cir.setReturnValue(TelemetryEventSender.DISABLED);
    }

    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/telemetry/TelemetryLogManager;open(Ljava/nio/file/Path;)Ljava/util/concurrent/CompletableFuture;"), require = 1)
    private CompletableFuture<Optional<TelemetryLogManager>> lads$noTelemetryFiles(Path path, Operation<CompletableFuture<Optional<TelemetryLogManager>>> original) {
        return CompletableFuture.completedFuture(Optional.empty());
    }
}
