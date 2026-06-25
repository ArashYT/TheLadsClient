/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.vertex.PoseStack
 *  dev.tr7zw.transition.mc.GeneralUtil
 *  dev.tr7zw.transition.mc.ItemUtil
 *  dev.tr7zw.transition.mc.MathUtil
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.model.ArmedModel
 *  net.minecraft.client.model.EntityModel
 *  net.minecraft.client.model.HumanoidModel
 *  net.minecraft.client.model.HumanoidModel$ArmPose
 *  net.minecraft.client.player.AbstractClientPlayer
 *  net.minecraft.client.renderer.SubmitNodeCollector
 *  net.minecraft.client.renderer.block.BlockModelRenderState
 *  net.minecraft.client.renderer.block.model.BlockDisplayContext
 *  net.minecraft.client.renderer.entity.state.EntityRenderState
 *  net.minecraft.client.renderer.entity.state.LivingEntityRenderState
 *  net.minecraft.client.renderer.texture.OverlayTexture
 *  net.minecraft.util.Mth
 *  net.minecraft.world.InteractionHand
 *  net.minecraft.world.entity.HumanoidArm
 *  net.minecraft.world.entity.LivingEntity
 *  net.minecraft.world.item.CrossbowItem
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.ProjectileWeaponItem
 *  net.minecraft.world.item.ShieldItem
 *  net.minecraft.world.level.block.Block
 *  net.minecraft.world.phys.Vec3
 *  org.joml.Matrix4fc
 *  org.joml.Quaternionfc
 *  org.joml.Vector4f
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package dev.tr7zw.notenoughanimations.logic;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.tr7zw.notenoughanimations.access.PlayerData;
import dev.tr7zw.notenoughanimations.mixins.EntityRenderDispatcherAccessor;
import dev.tr7zw.notenoughanimations.util.AnimationUtil;
import dev.tr7zw.notenoughanimations.util.MapRenderer;
import dev.tr7zw.notenoughanimations.util.NMSWrapper;
import dev.tr7zw.notenoughanimations.versionless.NEABaseMod;
import dev.tr7zw.notenoughanimations.versionless.animations.DataHolder;
import dev.tr7zw.transition.mc.GeneralUtil;
import dev.tr7zw.transition.mc.ItemUtil;
import dev.tr7zw.transition.mc.MathUtil;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import org.joml.Quaternionfc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public class HeldItemHandler
implements DataHolder<HeldItemHandler.HeldItemState> {
    private Item filledMap = ItemUtil.getItem((Identifier)GeneralUtil.getResourceLocation((String)"minecraft", (String)"filled_map"));
    private Set<Item> hideItemsForTheseBows = new HashSet<Item>();
    private Set<Item> lanternItems = new HashSet<Item>();

    public void onLoad() {
        this.hideItemsForTheseBows.clear();
        this.hideItemsForTheseBows.addAll(AnimationUtil.parseItemList(NEABaseMod.config.hideItemsForTheseBows));
        this.lanternItems.clear();
        this.lanternItems.addAll(AnimationUtil.parseItemList(NEABaseMod.config.lanternItems));
    }

    public void onRenderItem(LivingEntity entity, EntityModel<?> model, ItemStack itemStack, HumanoidArm arm, PoseStack matrices, SubmitNodeCollector vertexConsumers, LivingEntityRenderState livingEntityRenderState, int light, CallbackInfo info) {
        AbstractClientPlayer player;
        if (entity == null) {
            return;
        }
        if (entity.isSleeping()) {
            if (NEABaseMod.config.dontHoldItemsInBed) {
                info.cancel();
            }
            return;
        }
        if (NMSWrapper.hasCustomModel(itemStack)) {
            return;
        }
        if (model instanceof ArmedModel) {
            ArmedModel armedModel = (ArmedModel)model;
            if (model instanceof HumanoidModel) {
                HumanoidModel humanoid = (HumanoidModel)model;
                if (arm == HumanoidArm.RIGHT && humanoid.rightArm.visible || arm == HumanoidArm.LEFT && humanoid.leftArm.visible) {
                    if (NEABaseMod.config.enableInWorldMapRendering) {
                        if (arm == entity.getMainArm() && entity.getMainHandItem().getItem().equals(this.filledMap)) {
                            matrices.pushPose();
                            armedModel.translateToHand((EntityRenderState)livingEntityRenderState, arm, matrices);
                            matrices.mulPose((Quaternionfc)MathUtil.XP.rotationDegrees(-90.0f));
                            matrices.mulPose((Quaternionfc)MathUtil.YP.rotationDegrees(205.0f));
                            matrices.mulPose((Quaternionfc)MathUtil.ZP.rotationDegrees(10.0f));
                            boolean bl = arm == HumanoidArm.LEFT;
                            matrices.translate((double)((float)(bl ? -1 : 1) / 16.0f), 0.09 + (entity.getOffhandItem().isEmpty() ? 0.15 : 0.0), -0.625);
                            MapRenderer.renderFirstPersonMap(matrices, vertexConsumers, light, itemStack, !entity.getOffhandItem().isEmpty(), entity.getMainArm() == HumanoidArm.LEFT);
                            matrices.popPose();
                            info.cancel();
                            return;
                        }
                        if (arm != entity.getMainArm() && entity.getOffhandItem().getItem().equals(this.filledMap)) {
                            matrices.pushPose();
                            armedModel.translateToHand((EntityRenderState)livingEntityRenderState, arm, matrices);
                            matrices.mulPose((Quaternionfc)MathUtil.XP.rotationDegrees(-90.0f));
                            matrices.mulPose((Quaternionfc)MathUtil.YP.rotationDegrees(200.0f));
                            boolean bl = arm == HumanoidArm.LEFT;
                            matrices.translate((double)((float)(bl ? -1 : 1) / 16.0f), 0.125, -0.625);
                            MapRenderer.renderFirstPersonMap(matrices, vertexConsumers, light, itemStack, true, false);
                            matrices.popPose();
                            info.cancel();
                            return;
                        }
                    }
                    if (NEABaseMod.config.animateLanterns && entity instanceof PlayerData) {
                        PlayerData playerData = (PlayerData)entity;
                        if (this.lanternItems.contains(itemStack.getItem())) {
                            this.lanternAnimation(entity, playerData, itemStack, arm, matrices, vertexConsumers, livingEntityRenderState, armedModel, light);
                            info.cancel();
                            return;
                        }
                    }
                }
            }
        }
        if (NEABaseMod.config.enableOffhandHiding && entity instanceof AbstractClientPlayer && !((player = (AbstractClientPlayer)entity).getMainHandItem().getItem() instanceof ShieldItem)) {
            boolean mainHandProjectileWeapon = player.getMainHandItem().getItem() instanceof ProjectileWeaponItem;
            boolean offHandProjectileWeapon = player.getOffhandItem().getItem() instanceof ProjectileWeaponItem;
            if (!mainHandProjectileWeapon) {
                mainHandProjectileWeapon = this.hideItemsForTheseBows.contains(player.getMainHandItem().getItem());
            }
            if (!offHandProjectileWeapon) {
                offHandProjectileWeapon = this.hideItemsForTheseBows.contains(player.getOffhandItem().getItem());
            }
            boolean projectileWeaponEquipped = mainHandProjectileWeapon || offHandProjectileWeapon;
            boolean mainHandCharged = AnimationUtil.isChargedCrossbow(player.getMainHandItem());
            boolean offHandCharged = AnimationUtil.isChargedCrossbow(player.getOffhandItem());
            boolean isUsingItem = player.isUsingItem();
            if (!mainHandCharged && isUsingItem) {
                boolean bl = mainHandCharged = (float)(player.getMainHandItem().getUseDuration((LivingEntity)player) - player.getUseItemRemainingTicks()) / (float)CrossbowItem.getChargeDuration((ItemStack)player.getMainHandItem(), (LivingEntity)player) >= 1.0f;
            }
            if (!offHandCharged && isUsingItem) {
                offHandCharged = (float)(player.getOffhandItem().getUseDuration((LivingEntity)player) - player.getUseItemRemainingTicks()) / (float)CrossbowItem.getChargeDuration((ItemStack)player.getOffhandItem(), (LivingEntity)player) >= 1.0f;
            }
            HumanoidModel.ArmPose mainHandPose = AnimationUtil.getArmPose(player, InteractionHand.MAIN_HAND);
            HumanoidModel.ArmPose offHandPose = AnimationUtil.getArmPose(player, InteractionHand.OFF_HAND);
            if (!(AnimationUtil.isUsingBothHands(mainHandPose) || AnimationUtil.isUsingBothHands(offHandPose) || projectileWeaponEquipped && (mainHandCharged || offHandCharged || isUsingItem))) {
                return;
            }
            if (mainHandPose.isTwoHanded()) {
                offHandPose = player.getOffhandItem().isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
            }
            HumanoidArm mainArm = HumanoidArm.RIGHT;
            HumanoidArm offArm = HumanoidArm.LEFT;
            if (player.getMainArm() == HumanoidArm.LEFT) {
                mainArm = HumanoidArm.LEFT;
                offArm = HumanoidArm.RIGHT;
            }
            if (arm == mainArm && AnimationUtil.isUsingBothHands(offHandPose)) {
                info.cancel();
                return;
            }
            if (arm == offArm && AnimationUtil.isUsingBothHands(mainHandPose)) {
                info.cancel();
                return;
            }
            if (!(!projectileWeaponEquipped || !mainHandCharged && !offHandCharged && !isUsingItem || (mainHandCharged || offHandCharged) && isUsingItem || AnimationUtil.isUsingBothHands(mainHandPose) || AnimationUtil.isUsingBothHands(offHandPose))) {
                if (arm == mainArm && offHandProjectileWeapon && !mainHandProjectileWeapon) {
                    info.cancel();
                    return;
                }
                if (arm == offArm && mainHandProjectileWeapon) {
                    info.cancel();
                    return;
                }
            }
        }
    }

    private void lanternAnimation(LivingEntity entity, PlayerData playerData, ItemStack itemStack, HumanoidArm arm, PoseStack matrices, SubmitNodeCollector vertexConsumers, LivingEntityRenderState livingEntityRenderState, ArmedModel armedModel, int light) {
        double rawDelta;
        matrices.pushPose();
        armedModel.translateToHand((EntityRenderState)livingEntityRenderState, arm, matrices);
        matrices.scale(0.6f, 0.6f, 0.6f);
        HeldItemState state = playerData.getData(this, () -> new HeldItemState(entity));
        float chainOffset = 0.5f;
        float chainYOffset = 0.6f;
        matrices.translate(arm == HumanoidArm.LEFT ? -0.45 : -0.5, 0.4, -0.3);
        matrices.translate(chainOffset, chainYOffset, chainOffset);
        Vec3 camPos = Minecraft.getInstance().getEntityRenderDispatcher().camera.position();
        Vector4f origin = new Vector4f(0.0f, 0.0f, 0.0f, 1.0f);
        origin.mul((Matrix4fc)matrices.last().pose());
        Vec3 rawCurPos = camPos.add((double)origin.x, (double)origin.y, (double)origin.z);
        if (state.smoothedHandPos == null) {
            state.smoothedHandPos = rawCurPos;
        }
        if (!((rawDelta = state.smoothedHandPos.distanceToSqr(rawCurPos)) > 4.0)) {
            double alpha = 0.3;
            state.smoothedHandPos = new Vec3(Mth.lerp((double)alpha, (double)state.smoothedHandPos.x, (double)rawCurPos.x), Mth.lerp((double)alpha, (double)state.smoothedHandPos.y, (double)rawCurPos.y), Mth.lerp((double)alpha, (double)state.smoothedHandPos.z, (double)rawCurPos.z));
        }
        Vec3 curPos = state.smoothedHandPos;
        matrices.mulPose((Quaternionfc)MathUtil.XP.rotationDegrees(-90.0f));
        float yawRad = entity.getYRot() * ((float)Math.PI / 180);
        float pitchRad = entity.getXRot() * ((float)Math.PI / 180);
        Vec3 forward = new Vec3((double)(-Mth.sin((double)yawRad)), 0.0, (double)Mth.cos((double)yawRad)).normalize();
        Vec3 right = new Vec3(forward.z, 0.0, -forward.x);
        float delta = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Vec3 lerpedVelocity = state.lastLanternVelocity.add(state.lanternVelocity.subtract(state.lastLanternVelocity).multiply((double)delta, (double)delta, (double)delta));
        double forwardVel = lerpedVelocity.dot(forward);
        double rightVel = lerpedVelocity.dot(right);
        double verticalInfluence = -lerpedVelocity.y * (double)Mth.cos((double)pitchRad) * 0.6;
        float swingAngleX = Mth.clamp((float)((float)(forwardVel += verticalInfluence) * 90.0f), (float)-90.0f, (float)90.0f);
        float swingAngleZ = Mth.clamp((float)((float)(-rightVel) * 90.0f), (float)-90.0f, (float)90.0f);
        swingAngleX -= entity.getXRot() * 0.25f;
        double stiffness = 0.15;
        double damping = 0.95;
        Vec3 displacement = state.lanternPos.subtract(curPos);
        Vec3 acceleration = displacement.scale(-stiffness);
        if (entity.tickCount != state.lanternLastTick) {
            state.lanternLastTick = entity.tickCount;
            if (displacement.lengthSqr() > 100.0) {
                state.lastLanternVelocity = state.lanternVelocity;
                state.lanternVelocity = acceleration;
                state.lanternPos = curPos;
            } else {
                state.lastLanternVelocity = state.lanternVelocity;
                state.lanternVelocity = state.lanternVelocity.add(acceleration);
                state.lanternVelocity = state.lanternVelocity.scale(damping);
                state.lanternPos = state.lanternPos.add(state.lanternVelocity);
            }
        }
        matrices.mulPose((Quaternionfc)MathUtil.XP.rotationDegrees(swingAngleX));
        matrices.mulPose((Quaternionfc)MathUtil.ZP.rotationDegrees(swingAngleZ));
        matrices.translate(-chainOffset, -chainYOffset, -chainOffset);
        BlockModelRenderState blockState = new BlockModelRenderState();
        ((EntityRenderDispatcherAccessor)Minecraft.getInstance().getEntityRenderDispatcher()).nea$getBlockModelResolver().update(blockState, Block.byItem((Item)itemStack.getItem()).defaultBlockState(), BlockDisplayContext.create());
        blockState.submit(matrices, vertexConsumers, light, OverlayTexture.NO_OVERLAY, 0);
        matrices.popPose();
    }

    public static class HeldItemState {
        public int lanternLastTick = 0;
        public Vec3 lanternPos;
        public Vec3 lanternVelocity = Vec3.ZERO;
        public Vec3 lastLanternVelocity = Vec3.ZERO;
        public Vec3 smoothedHandPos = null;

        public HeldItemState(LivingEntity entity) {
            this.lanternLastTick = entity.tickCount;
            this.lanternPos = new Vec3(entity.getX(), entity.getY(), entity.getZ());
        }
    }
}

