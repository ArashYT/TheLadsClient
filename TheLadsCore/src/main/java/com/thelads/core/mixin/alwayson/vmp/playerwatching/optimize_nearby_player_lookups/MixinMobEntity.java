/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.EntitySelector
 *  net.minecraft.world.entity.EntityType
 *  net.minecraft.world.entity.LivingEntity
 *  net.minecraft.world.entity.Mob
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.level.Level
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Redirect
 */
package com.thelads.core.mixin.alwayson.vmp.playerwatching.optimize_nearby_player_lookups;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value={Mob.class})
public abstract class MixinMobEntity
extends LivingEntity {
    protected MixinMobEntity(EntityType<? extends LivingEntity> entityType, Level world) {
        super(entityType, world);
    }

    @Redirect(method={"checkDespawn"}, at=@At(value="INVOKE", target="Lnet/minecraft/world/level/Level;getNearestPlayer(Lnet/minecraft/world/entity/Entity;D)Lnet/minecraft/world/entity/player/Player;"))
    private Player redirectGetClosestPlayer(Level instance, Entity entity, double maxDistance) {
        Player closestPlayer = instance.getNearestPlayer(entity, (double)this.getType().getCategory().getDespawnDistance());
        if (closestPlayer != null) {
            return closestPlayer;
        }
        for (Player player : this.level().players()) {
            if (!EntitySelector.NO_SPECTATORS.test(player)) continue;
            return player;
        }
        return null;
    }
}

