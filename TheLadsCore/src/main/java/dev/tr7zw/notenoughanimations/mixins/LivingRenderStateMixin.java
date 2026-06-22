/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.renderer.entity.state.LivingEntityRenderState
 *  net.minecraft.world.entity.LivingEntity
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Unique
 */
package dev.tr7zw.notenoughanimations.mixins;

import dev.tr7zw.notenoughanimations.access.ExtendedLivingRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value={LivingEntityRenderState.class})
public class LivingRenderStateMixin
implements ExtendedLivingRenderState {
    @Unique
    private LivingEntity entity;

    @Override
    public void setEntity(LivingEntity player) {
        this.entity = player;
    }

    @Override
    public LivingEntity getEntity() {
        return this.entity;
    }
}

