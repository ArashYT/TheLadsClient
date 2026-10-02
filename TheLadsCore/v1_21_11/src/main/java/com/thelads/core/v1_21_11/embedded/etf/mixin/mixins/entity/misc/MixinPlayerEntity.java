package com.thelads.core.v1_21_11.embedded.etf.mixin.mixins.entity.misc;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import com.thelads.core.v1_21_11.embedded.etf.features.player.ETFPlayerEntity;

@Mixin(net.minecraft.world.entity.Avatar.class)
public abstract class MixinPlayerEntity extends Entity implements ETFPlayerEntity {

    @SuppressWarnings("unused")
    public MixinPlayerEntity(EntityType<?> type, Level world) {
        super(type, world);
    }

    @Override
    public Entity etf$getEntity() {
        return this;
    }

    @Override
    public boolean etf$isTeammate(Player player) {
        return isAlliedTo(player);
    }

    @Override
    public Inventory etf$getInventory() {
        if (((Object) this) instanceof Player player) {
            return player.getInventory();
        }
        return null;
    }

    @Override
    @Deprecated
    public boolean etf$isPartVisible(PlayerModelPart part) {
        return false;
    }

    @Override
    public Component etf$getName() {
        return getName();
    }

    @Override
    public String etf$getUuidAsString() {
        return getStringUUID();
    }

}


