/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.authlib.GameProfile
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.phys.Vec3
 *  org.spongepowered.asm.mixin.Mixin
 */
package com.thelads.core.mixin.alwayson.vmp.playerwatching.optimize_nearby_entity_tracking_lookups;

import com.thelads.core.features.alwayson.vmp.common.playerwatching.ServerPlayerEntityExtension;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value={ServerPlayer.class})
public abstract class MixinServerPlayerEntity
extends Player
implements ServerPlayerEntityExtension {
    private double vmpTracking$prevX = Double.NaN;
    private double vmpTracking$prevY = Double.NaN;
    private double vmpTracking$prevZ = Double.NaN;

    public MixinServerPlayerEntity(Level world, GameProfile profile) {
        super(world, profile);
    }

    @Override
    public boolean vmpTracking$isPositionUpdated() {
        Vec3 pos = this.position();
        return pos.x != this.vmpTracking$prevX || pos.y != this.vmpTracking$prevY || pos.z != this.vmpTracking$prevZ;
    }

    @Override
    public void vmpTracking$updatePosition() {
        Vec3 pos = this.position();
        this.vmpTracking$prevX = pos.x;
        this.vmpTracking$prevY = pos.y;
        this.vmpTracking$prevZ = pos.z;
    }
}

