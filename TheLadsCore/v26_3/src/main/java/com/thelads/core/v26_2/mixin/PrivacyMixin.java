package com.thelads.core.v26_2.mixin;
import net.minecraft.client.telemetry.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
@Mixin(ClientTelemetryManager.class)
public class PrivacyMixin {
    @Inject(method="createEventSender",at=@At("HEAD"),cancellable=true,require=1)
    private void ladsDisableTelemetry(CallbackInfoReturnable<TelemetryEventSender> ci){ci.setReturnValue(TelemetryEventSender.DISABLED);}
    @Redirect(method="<init>",at=@At(value="INVOKE",target="Lnet/minecraft/client/telemetry/TelemetryLogManager;open(Ljava/nio/file/Path;)Ljava/util/concurrent/CompletableFuture;"),require=1)
    private CompletableFuture<Optional<TelemetryLogManager>> ladsNoTelemetryFiles(Path path){return CompletableFuture.completedFuture(Optional.empty());}
}
