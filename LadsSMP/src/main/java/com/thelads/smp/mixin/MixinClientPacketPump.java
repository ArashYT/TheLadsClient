package com.thelads.smp.mixin;

import com.thelads.smp.client.LoadingPump;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Marks the client's packet handling, where JEI and the creative-tab rebuild run right after the login packet. */
@Mixin(PacketProcessor.class)
public abstract class MixinClientPacketPump {
    @Inject(method = "processQueuedPackets", at = @At("HEAD"), require = 0)
    private void ladssmp$start(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.isSameThread() && mc.packetProcessor() == (Object) this) LoadingPump.packetsStart();
    }

    @Inject(method = "processQueuedPackets", at = @At("RETURN"), require = 0)
    private void ladssmp$end(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.isSameThread() && mc.packetProcessor() == (Object) this) LoadingPump.packetsEnd();
    }
}
