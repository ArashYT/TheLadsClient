package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.client.DamageTilt;
import com.thelads.core.v1_8_9.feature.KillBanner189;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S1CPacketEntityMetadata;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * KillBanner: deaths as the server reports them, as each packet is applied on the client thread (off it, the handlers return at
 * checkThreadAndEnqueue, before TAIL): the death status (3) and a health update to zero. Damage tilt (common DamageTilt): hurts and
 * knockback, paired there; nothing here changes what 1.8.9 does with the packets.
 */
@Mixin(NetHandlerPlayClient.class)
public abstract class NetHandlerPlayClientMixin {
    @Inject(method = "handleEntityStatus", at = @At("TAIL"), require = 1)
    private void ladsDeath(S19PacketEntityStatus packet, CallbackInfo ci) {
        if (packet.getOpCode() == 3 && Minecraft.getMinecraft().theWorld != null) KillBanner189.died(packet.getEntity(Minecraft.getMinecraft().theWorld));
    }

    /** Damage tilt: the client player's hurt (status 2). The server never sends 1.8.9 clients the hit's direction... */
    @Inject(method = "handleEntityStatus", at = @At("TAIL"), require = 1)
    private void ladsHurt(S19PacketEntityStatus packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        if (packet.getOpCode() == 2 && mc.thePlayer != null && packet.getEntity(mc.theWorld) == mc.thePlayer)
            DamageTilt.CLIENT.hurt(System.currentTimeMillis());
    }

    /** ...but sends the knockback that pushes the player away from the attacker in the same tick: its opposite is the direction. */
    @Inject(method = "handleEntityVelocity", at = @At("TAIL"), require = 1)
    private void ladsKnockback(S12PacketEntityVelocity packet, CallbackInfo ci) {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player != null && packet.getEntityID() == player.getEntityId()) DamageTilt.CLIENT.direction(
            DamageTilt.sourceYaw(packet.getMotionX() / 8000.0, packet.getMotionZ() / 8000.0, player.rotationYaw), System.currentTimeMillis());
    }

    @Inject(method = "handleEntityMetadata", at = @At("TAIL"), require = 1)
    private void ladsHealth(S1CPacketEntityMetadata packet, CallbackInfo ci) {
        Entity entity = Minecraft.getMinecraft().theWorld != null ? Minecraft.getMinecraft().theWorld.getEntityByID(packet.getEntityId()) : null;
        if (entity instanceof EntityLivingBase && ((EntityLivingBase) entity).getHealth() <= 0) KillBanner189.died(entity);
    }
}
