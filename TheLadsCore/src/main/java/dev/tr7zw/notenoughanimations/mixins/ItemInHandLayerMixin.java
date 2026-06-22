/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.vertex.PoseStack
 *  net.minecraft.client.model.EntityModel
 *  net.minecraft.client.renderer.SubmitNodeCollector
 *  net.minecraft.client.renderer.entity.RenderLayerParent
 *  net.minecraft.client.renderer.entity.layers.ItemInHandLayer
 *  net.minecraft.client.renderer.entity.layers.RenderLayer
 *  net.minecraft.client.renderer.entity.state.ArmedEntityRenderState
 *  net.minecraft.client.renderer.entity.state.LivingEntityRenderState
 *  net.minecraft.client.renderer.item.ItemStackRenderState
 *  net.minecraft.world.entity.HumanoidArm
 *  net.minecraft.world.entity.LivingEntity
 *  net.minecraft.world.item.ItemStack
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package dev.tr7zw.notenoughanimations.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.tr7zw.notenoughanimations.NEAnimationsLoader;
import dev.tr7zw.notenoughanimations.access.ExtendedItemStackRenderState;
import dev.tr7zw.notenoughanimations.access.ExtendedLivingRenderState;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={ItemInHandLayer.class})
public abstract class ItemInHandLayerMixin<S extends ArmedEntityRenderState, M extends EntityModel<S>>
extends RenderLayer<S, M> {
    public ItemInHandLayerMixin(RenderLayerParent<S, M> renderer) {
        super(renderer);
    }

    @Inject(at={@At(value="HEAD")}, method={"submitArmWithItem"}, cancellable=true)
    private void submitArmWithItem(S armedEntityRenderState, ItemStackRenderState itemStackRenderState, ItemStack passedItem, HumanoidArm humanoidArm, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, CallbackInfo ci) {
        if (!com.thelads.core.config.ModuleManager.getInstance().getModule("NotEnoughAnimations").isEnabled()) {
            return;
        }
        ExtendedItemStackRenderState ext;
        LivingEntity livingEntity = ((ExtendedLivingRenderState)armedEntityRenderState).getEntity();
        ItemStack itemStack = null;
        itemStack = passedItem;
        if (itemStackRenderState instanceof ExtendedItemStackRenderState && (ext = (ExtendedItemStackRenderState)itemStackRenderState).getItemStack() != null) {
            itemStack = ext.getItemStack();
        } else if (itemStack == null) {
            return;
        }
        NEAnimationsLoader.INSTANCE.heldItemHandler.onRenderItem(livingEntity, (EntityModel<?>)this.getParentModel(), itemStack, humanoidArm, poseStack, submitNodeCollector, (LivingEntityRenderState)armedEntityRenderState, i, ci);
    }
}

