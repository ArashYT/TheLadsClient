/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.model.HumanoidModel
 *  net.minecraft.client.player.AbstractClientPlayer
 *  net.minecraft.client.renderer.entity.EntityRendererProvider$Context
 *  net.minecraft.client.renderer.entity.LivingEntityRenderer
 *  net.minecraft.client.renderer.entity.player.AvatarRenderer
 *  net.minecraft.client.renderer.entity.state.AvatarRenderState
 *  net.minecraft.client.renderer.entity.state.HumanoidRenderState
 *  net.minecraft.world.entity.Avatar
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package dev.tr7zw.notenoughanimations.mixins;

import dev.tr7zw.notenoughanimations.access.PlayerData;
import dev.tr7zw.notenoughanimations.util.RenderStateHolder;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={AvatarRenderer.class})
public abstract class PlayerRendererMixin
extends LivingEntityRenderer<AbstractClientPlayer, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
    public PlayerRendererMixin(EntityRendererProvider.Context context, HumanoidModel<HumanoidRenderState> model, float shadowRadius) {
        super(context, model, shadowRadius);
    }

    @Inject(method={"<init>*"}, at={@At(value="RETURN")})
    public void onCreate(CallbackInfo info) {
    }

    @Inject(method={"extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V"}, at={@At(value="HEAD")})
    private void includeData(Avatar abstractClientPlayer, AvatarRenderState playerRenderState, float f, CallbackInfo ci) {
        if (!com.thelads.core.config.ModuleManager.getInstance().getModule("NotEnoughAnimations").isEnabled()) {
            return;
        }
        if (abstractClientPlayer instanceof PlayerData) {
            PlayerData playerData = (PlayerData)abstractClientPlayer;
            RenderStateHolder.RenderStateData data = playerData.getData(RenderStateHolder.INSTANCE, RenderStateHolder.RenderStateData::new);
            data.renderState = playerRenderState;
        }
    }
}

