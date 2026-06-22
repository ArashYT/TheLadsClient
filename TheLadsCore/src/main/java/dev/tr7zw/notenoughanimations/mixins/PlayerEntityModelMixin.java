/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.model.HumanoidModel
 *  net.minecraft.client.model.player.PlayerModel
 *  net.minecraft.client.player.AbstractClientPlayer
 *  net.minecraft.client.renderer.entity.state.AvatarRenderState
 *  net.minecraft.world.entity.LivingEntity
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package dev.tr7zw.notenoughanimations.mixins;

import dev.tr7zw.notenoughanimations.NEAnimationsLoader;
import dev.tr7zw.notenoughanimations.access.ExtendedLivingRenderState;
import dev.tr7zw.notenoughanimations.access.PlayerData;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={PlayerModel.class})
public abstract class PlayerEntityModelMixin
extends HumanoidModel<AvatarRenderState> {
    @Unique
    private static final String SETUP_ANIM_METHOD = "setupAnim";

    public PlayerEntityModelMixin() {
        super(null);
    }

    @Inject(method={"setupAnim"}, at={@At(value="HEAD")})
    public void setupAnimHEAD(AvatarRenderState state, CallbackInfo info) {
        if (!com.thelads.core.config.ModuleManager.getInstance().getModule("NotEnoughAnimations").isEnabled()) {
            return;
        }
        LivingEntity livingEntity;
        if (state == null || !(state instanceof ExtendedLivingRenderState)) {
            return;
        }
        float limbSwing = state.walkAnimationPos;
        PlayerModel model = (PlayerModel)(Object)this;
        AbstractClientPlayer player = null;
        if (((ExtendedLivingRenderState)state).getEntity() != null && (livingEntity = ((ExtendedLivingRenderState)state).getEntity()) instanceof AbstractClientPlayer) {
            AbstractClientPlayer p;
            player = p = (AbstractClientPlayer)livingEntity;
        }
        if (player == null) {
            return;
        }
        NEAnimationsLoader.INSTANCE.playerTransformer.preUpdate(player, model, limbSwing, info);
    }

    @Inject(method={"setupAnim"}, at={@At(value="RETURN")})
    public void setupAnim(AvatarRenderState state, CallbackInfo info) {
        if (!com.thelads.core.config.ModuleManager.getInstance().getModule("NotEnoughAnimations").isEnabled()) {
            return;
        }
        LivingEntity livingEntity;
        float limbSwing = state.walkAnimationPos;
        PlayerModel model = (PlayerModel)(Object)this;
        AbstractClientPlayer player = null;
        if (((ExtendedLivingRenderState)state).getEntity() != null && (livingEntity = ((ExtendedLivingRenderState)state).getEntity()) instanceof AbstractClientPlayer) {
            AbstractClientPlayer p;
            player = p = (AbstractClientPlayer)livingEntity;
        }
        if (player == null) {
            return;
        }
        NEAnimationsLoader.INSTANCE.playerTransformer.updateModel(player, model, limbSwing, info);
    }

    @Inject(method={"setupAnim"}, at={@At(value="RETURN")})
    public void setupAnimEnd(AvatarRenderState state, CallbackInfo info) {
        if (!com.thelads.core.config.ModuleManager.getInstance().getModule("NotEnoughAnimations").isEnabled()) {
            return;
        }
        LivingEntity livingEntity;
        AbstractClientPlayer player = null;
        if (((ExtendedLivingRenderState)state).getEntity() != null && (livingEntity = ((ExtendedLivingRenderState)state).getEntity()) instanceof AbstractClientPlayer) {
            AbstractClientPlayer p;
            player = p = (AbstractClientPlayer)livingEntity;
        }
        if (player == null) {
            return;
        }
        PlayerData data = (PlayerData)player;
        if (data.getPoseOverwrite() != null) {
            player.setPose(data.getPoseOverwrite());
            data.setPoseOverwrite(null);
        }
    }
}

