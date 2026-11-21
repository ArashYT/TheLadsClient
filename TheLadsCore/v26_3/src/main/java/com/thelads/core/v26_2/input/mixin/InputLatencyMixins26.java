package com.thelads.core.v26_2.input.mixin;

import com.mojang.blaze3d.platform.SDLEventHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import com.thelads.core.v26_2.input.InputLatency26;
import com.thelads.core.v26_2.input.LowLatencyDebugEntry26;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.client.gui.components.debug.DebugScreenProfile;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** InputLatency26's hooks, and the Low Latency module's debug screen entry. */
public final class InputLatencyMixins26 {
    private InputLatencyMixins26() {}

    @Mixin(RenderSystem.class)
    public static class Poll {
        @Inject(method = "pollEvents", at = @At("HEAD"), require = 0)
        private static void lads$frameStart(SDLEventHandler handler, CallbackInfo ci) {
            InputLatency26.frameStart();
        }
    }

    @Mixin(Minecraft.class)
    public static class Submit {
        @Inject(method = "renderFrame", at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/api/commands/CommandEncoder;submit()V"), require = 0)
        private void lads$beforeSubmit(boolean advanceGameTime, CallbackInfo ci) {
            InputLatency26.beforeSubmit();
        }

        @Inject(method = "renderFrame", at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/api/device/GpuSurface;present()V", shift = At.Shift.AFTER), require = 0)
        private void lads$afterPresent(boolean advanceGameTime, CallbackInfo ci) {
            InputLatency26.afterPresent();
        }
    }

    /** Registers the Low Latency debug section and shows it in every F3 profile (the entry itself is empty while the overlay option is off). */
    @Mixin(DebugScreenEntries.class)
    public abstract static class DebugEntries {
        @Shadow @Final @Mutable public static Map<DebugScreenProfile, Map<Identifier, DebugScreenEntryStatus>> PROFILES;

        @Shadow
        private static Identifier register(final Identifier identifier, final DebugScreenEntry entry) {
            return null;
        }

        @Inject(method = "<clinit>", at = @At("RETURN"), require = 0)
        private static void lads$lowLatencyEntry(final CallbackInfo ci) {
            final Identifier id = register(LowLatencyDebugEntry26.ENTRY_ID, new LowLatencyDebugEntry26());
            final Map<DebugScreenProfile, Map<Identifier, DebugScreenEntryStatus>> profiles = new HashMap<>();
            for (Map.Entry<DebugScreenProfile, Map<Identifier, DebugScreenEntryStatus>> entry : PROFILES.entrySet()) {
                final Map<Identifier, DebugScreenEntryStatus> entries = new HashMap<>(entry.getValue());
                entries.put(id, DebugScreenEntryStatus.IN_OVERLAY);
                profiles.put(entry.getKey(), entries);
            }
            PROFILES = profiles;
        }
    }
}
