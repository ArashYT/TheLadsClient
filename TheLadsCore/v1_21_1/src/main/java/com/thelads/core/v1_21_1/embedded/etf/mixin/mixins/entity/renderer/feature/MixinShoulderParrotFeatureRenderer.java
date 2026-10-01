package com.thelads.core.v1_21_1.embedded.etf.mixin.mixins.entity.renderer.feature;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.ParrotOnShoulderLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EntityType;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v1_21_1.embedded.etf.features.state.ETFEntityRenderState;
import com.thelads.core.v1_21_1.embedded.etf.features.state.ETFState;
import com.thelads.core.v1_21_1.embedded.etf.features.state.HoldsETFRenderState;
import com.thelads.core.v1_21_1.embedded.etf.utils.ETFEntity;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.thelads.core.v1_21_1.embedded.etf.utils.UEntityTypes;
@Mixin(ParrotOnShoulderLayer.class)
public abstract class MixinShoulderParrotFeatureRenderer<T extends Player> extends RenderLayer<T, PlayerModel<T>> {

@SuppressWarnings("unused")
public MixinShoulderParrotFeatureRenderer(RenderLayerParent<T, PlayerModel<T>> context) {
    super(context);
}

    // cant target lambda directly with forge
    @ModifyArg(method = "Lnet/minecraft/client/renderer/entity/layers/ParrotOnShoulderLayer;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/player/Player;FFFFZ)V",
            at = @At(value = "INVOKE", target = "Ljava/util/Optional;ifPresent(Ljava/util/function/Consumer;)V"))
    private Consumer<EntityType<?>> etf$alterEntity(final Consumer<EntityType<?>> action, @Local(argsOnly = true) T playerEntity, @Local CompoundTag nbtCompound) {
        return (v)-> {
            if (nbtCompound != null) {
                Optional<Entity> optionalEntity = EntityType.create(nbtCompound, playerEntity.level());
                if (optionalEntity.isPresent() && optionalEntity.get() instanceof Parrot parrot) {
                    ETFState.mount(ETFEntityRenderState.forEntity((ETFEntity) parrot));
                } else {
                    ETFState.mountNone();
                }
            } else {
                ETFState.mountNone();
            }
            action.accept(v);
            ETFState.unMount();
        };
    }

}


