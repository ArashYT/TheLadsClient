package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.client.DamageTilt;
import com.thelads.core.modules.ComboSoundsModule;
import com.thelads.core.v1_8_9.feature.KillBanner189;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S1CPacketEntityMetadata;
import net.minecraft.network.play.server.S2BPacketChangeGameState;
import net.minecraft.network.play.server.S38PacketPlayerListItem;
import net.minecraft.network.play.server.S3EPacketTeams;
import net.minecraft.util.IChatComponent;
import com.thelads.core.v1_8_9.feature.TabListCache189;
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
        // Combo Sounds: the hurt that confirms your hit, or your own (it ends the combo).
        Entity hurt = packet.getOpCode() == 2 && mc.thePlayer != null && mc.theWorld != null ? packet.getEntity(mc.theWorld) : null;
        if (hurt != null && hurt != mc.thePlayer) KillBanner189.reach(hurt); // an arrow of yours, a tick behind the server's
        if (hurt != null) ComboSoundsModule.onHurt(hurt.getEntityId(), hurt == mc.thePlayer, hurt instanceof EntityPlayer, mc.thePlayer.getDistanceToEntity(hurt));
    }

    /** ...but sends the knockback that pushes the player away from the attacker in the same tick: its opposite is the direction. */
    @Inject(method = "handleEntityVelocity", at = @At("TAIL"), require = 1)
    private void ladsKnockback(S12PacketEntityVelocity packet, CallbackInfo ci) {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player != null && packet.getEntityID() == player.getEntityId()) DamageTilt.CLIENT.direction(
            DamageTilt.sourceYaw(packet.getMotionX() / 8000.0, packet.getMotionZ() / 8000.0, player.rotationYaw), System.currentTimeMillis());
    }

    /** Combo Sounds: your arrow hit a player (change game state 6, the server's arrow-hit sound). */
    @Inject(method = "handleChangeGameState", at = @At("TAIL"), require = 1)
    private void ladsArrowHitPlayer(S2BPacketChangeGameState packet, CallbackInfo ci) {
        if (packet.getGameState() == 6) KillBanner189.arrowHitPlayer();
    }

    @Inject(method = "handleEntityMetadata", at = @At("TAIL"), require = 1)
    private void ladsHealth(S1CPacketEntityMetadata packet, CallbackInfo ci) {
        Entity entity = Minecraft.getMinecraft().theWorld != null ? Minecraft.getMinecraft().theWorld.getEntityByID(packet.getEntityId()) : null;
        if (entity instanceof EntityLivingBase && ((EntityLivingBase) entity).getHealth() <= 0) KillBanner189.died(entity);
    }

    @Inject(method = {"handlePlayerListItem", "handleTeams"}, at = @At("TAIL"), require = 1)
    private void ladsInvalidateTabList(CallbackInfo ci) {
        TabListCache189.invalidate();
    }

    @Inject(method = {"cleanup", "onDisconnect"}, at = @At("HEAD"), require = 1)
    private void ladsResetTabList(CallbackInfo ci) {
        TabListCache189.invalidate();
    }
}
