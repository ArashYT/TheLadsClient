package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.KillBanner189;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S1CPacketEntityMetadata;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * KillBanner: deaths as the server reports them, as each packet is applied on the client thread (off it, the handlers return at
 * checkThreadAndEnqueue, before TAIL): the death status (3) and a health update to zero.
 */
@Mixin(NetHandlerPlayClient.class)
public abstract class NetHandlerPlayClientMixin {
    @Inject(method = "handleEntityStatus", at = @At("TAIL"), require = 1)
    private void ladsDeath(S19PacketEntityStatus packet, CallbackInfo ci) {
        if (packet.getOpCode() == 3 && Minecraft.getMinecraft().theWorld != null) KillBanner189.died(packet.getEntity(Minecraft.getMinecraft().theWorld));
    }

    @Inject(method = "handleEntityMetadata", at = @At("TAIL"), require = 1)
    private void ladsHealth(S1CPacketEntityMetadata packet, CallbackInfo ci) {
        Entity entity = Minecraft.getMinecraft().theWorld != null ? Minecraft.getMinecraft().theWorld.getEntityByID(packet.getEntityId()) : null;
        if (entity instanceof EntityLivingBase && ((EntityLivingBase) entity).getHealth() <= 0) KillBanner189.died(entity);
    }
}
