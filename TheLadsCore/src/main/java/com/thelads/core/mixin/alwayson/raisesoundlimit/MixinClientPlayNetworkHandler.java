/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.llamalad7.mixinextras.injector.ModifyExpressionValue
 *  com.llamalad7.mixinextras.injector.wrapoperation.Operation
 *  com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
 *  com.llamalad7.mixinextras.sugar.Share
 *  com.llamalad7.mixinextras.sugar.ref.LocalRef
 *  net.minecraft.client.multiplayer.ClientLevel
 *  net.minecraft.client.multiplayer.ClientPacketListener
 *  net.minecraft.network.PacketListener
 *  net.minecraft.network.protocol.Packet
 *  net.minecraft.network.protocol.game.ClientboundSoundPacket
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Coerce
 */
package com.thelads.core.mixin.alwayson.raisesoundlimit;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin(value={ClientPacketListener.class})
public class MixinClientPlayNetworkHandler {
    @WrapOperation(method={"handleSoundEvent"}, at={@At(value="INVOKE", target="Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V")}, require=1)
    private <T extends PacketListener> void handleSoundsAsync(Packet<T> packet, T listener, @Coerce Object thirdParam, Operation<Void> original, @Share(value="rsls$thirdParam") LocalRef<Object> rsls$thirdParam, @Share(value="rsls$originalOp") LocalRef<Operation<Void>> rsls$originalOperation) {
        rsls$originalOperation.set(original);
        rsls$thirdParam.set(thirdParam);
    }

    @ModifyExpressionValue(method={"handleSoundEvent"}, at={@At(value="FIELD", target="Lnet/minecraft/client/Minecraft;level:Lnet/minecraft/client/multiplayer/ClientLevel;", opcode=180)})
    private <T extends PacketListener> ClientLevel checkWorldExistence(ClientLevel world, ClientboundSoundPacket packet, @Share(value="rsls$thirdParam") LocalRef<Object> rsls$thirdParam, @Share(value="rsls$originalOp") LocalRef<Operation<Void>> rsls$originalOperation) {
        if (world == null) {
            ((Operation)rsls$originalOperation.get()).call(new Object[]{packet, this, rsls$thirdParam.get()});
            return null;
        }
        return world;
    }
}

