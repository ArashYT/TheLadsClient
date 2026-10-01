package com.thelads.core.v1_21_11.embedded.etf.mixin.mixins.entity.renderer.feature;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.ParrotOnShoulderLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EntityType;

import net.minecraft.client.renderer.entity.state.ParrotRenderState;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v1_21_11.embedded.etf.features.state.ETFEntityRenderState;
import com.thelads.core.v1_21_11.embedded.etf.features.state.ETFState;
import com.thelads.core.v1_21_11.embedded.etf.features.state.HoldsETFRenderState;
import com.thelads.core.v1_21_11.embedded.etf.utils.ETFEntity;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.thelads.core.v1_21_11.embedded.etf.utils.UEntityTypes;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.EntitySpawnReason;

@Mixin(ParrotOnShoulderLayer.class)
public abstract class MixinShoulderParrotFeatureRenderer extends RenderLayer<AvatarRenderState, PlayerModel> {


    public MixinShoulderParrotFeatureRenderer() {super(null);}

    @Inject(method = "submitOnShoulder", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"
    ))
    private void etf$modifySubmit(final PoseStack poseStack, final SubmitNodeCollector submitNodeCollector, final int i, final AvatarRenderState avatarRenderState, final Parrot.Variant variant, final float f, final float g, final boolean bl, final CallbackInfo ci,
                                  @Local ParrotRenderState parrotRenderState) {
        var state =  ((HoldsETFRenderState) avatarRenderState).etf$getState();
        if (state != null && state.entity() instanceof Player playerEntity) {
            etf$setParrotAsCurrentEntity(playerEntity, parrotRenderState);
        }
    }

    @Unique
    private void etf$setParrotAsCurrentEntity(final Player playerEntity, final ParrotRenderState parrotRenderState) {
        if (parrotRenderState != null) {
            try {
                var parrot = UEntityTypes.PARROT.create(playerEntity.level(), EntitySpawnReason.COMMAND);
                var state = ETFEntityRenderState.forEntity((ETFEntity) parrot);
                ((HoldsETFRenderState) parrotRenderState).etf$initState((ETFEntity) parrot);
                ETFState.mount(state);
            } catch (final Exception ignored) {
                ETFState.mountNone();
            }
        } else {
            ETFState.mountNone();
        }
    }

    @Inject(method = "submitOnShoulder", at = @At(value = "RETURN"))
    private void etf$resetEntity(CallbackInfo ci) {
        ETFState.unMount();
    }

}


