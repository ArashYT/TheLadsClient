/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.server.level.ClientInformation
 *  net.minecraft.server.level.ServerPlayer
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.thelads.core.mixin.alwayson.vmp.playerwatching;

import com.thelads.core.features.alwayson.vmp.common.chunkwatching.PlayerClientVDTracking;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={ServerPlayer.class})
public class MixinServerPlayerEntity
implements PlayerClientVDTracking {
    @Unique
    private boolean vdChanged = false;
    @Unique
    private int clientVD = 2;

    @Inject(method={"updateOptions"}, at={@At(value="HEAD")})
    private void onClientSettingsChanged(ClientInformation packet, CallbackInfo ci) {
        int currentVD = packet.viewDistance();
        if (currentVD != this.clientVD) {
            this.vdChanged = true;
        }
        this.clientVD = Math.max(2, currentVD);
    }

    @Inject(method={"restoreFrom"}, at={@At(value="RETURN")})
    private void onPlayerCopy(ServerPlayer oldPlayer, boolean alive, CallbackInfo ci) {
        this.clientVD = ((PlayerClientVDTracking)oldPlayer).getClientViewDistance();
        this.vdChanged = true;
    }

    @Override
    @Unique
    public boolean isClientViewDistanceChanged() {
        return this.vdChanged;
    }

    @Override
    @Unique
    public int getClientViewDistance() {
        this.vdChanged = false;
        return this.clientVD;
    }
}

