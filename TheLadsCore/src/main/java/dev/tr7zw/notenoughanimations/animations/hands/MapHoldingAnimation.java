/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  dev.tr7zw.transition.mc.EntityUtil
 *  net.minecraft.client.model.player.PlayerModel
 *  net.minecraft.client.player.AbstractClientPlayer
 *  net.minecraft.util.Mth
 *  net.minecraft.world.InteractionHand
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.HumanoidArm
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.ItemStack
 */
package dev.tr7zw.notenoughanimations.animations.hands;

import dev.tr7zw.notenoughanimations.NEAnimationsMod;
import dev.tr7zw.notenoughanimations.access.PlayerData;
import dev.tr7zw.notenoughanimations.api.BasicAnimation;
import dev.tr7zw.notenoughanimations.util.AnimationUtil;
import dev.tr7zw.notenoughanimations.util.NMSWrapper;
import dev.tr7zw.notenoughanimations.versionless.NEABaseMod;
import dev.tr7zw.notenoughanimations.versionless.animations.BodyPart;
import dev.tr7zw.transition.mc.EntityUtil;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class MapHoldingAnimation
extends BasicAnimation {
    private Set<Item> compatibleMaps = new HashSet<Item>();
    private final BodyPart[] bothHands = new BodyPart[]{BodyPart.LEFT_ARM, BodyPart.RIGHT_ARM};
    private final BodyPart[] left = new BodyPart[]{BodyPart.LEFT_ARM};
    private final BodyPart[] right = new BodyPart[]{BodyPart.RIGHT_ARM};
    private BodyPart[] target = this.bothHands;

    @Override
    public boolean isEnabled() {
        this.bind();
        return NEABaseMod.config.enableInWorldMapRendering || !this.compatibleMaps.isEmpty();
    }

    private void bind() {
        this.compatibleMaps.clear();
        this.compatibleMaps.addAll(AnimationUtil.parseItemList(NEAnimationsMod.config.mapHolding));
    }

    @Override
    public boolean isValid(AbstractClientPlayer entity, PlayerData data) {
        ItemStack itemInMainHand = entity.getItemInHand(InteractionHand.MAIN_HAND);
        ItemStack itemInOffHand = entity.getItemInHand(InteractionHand.OFF_HAND);
        if (this.compatibleMaps.contains(itemInMainHand.getItem()) && itemInOffHand.isEmpty()) {
            if (NMSWrapper.hasCustomModel(itemInMainHand)) {
                return false;
            }
            this.target = this.bothHands;
            return true;
        }
        if (this.compatibleMaps.contains(itemInMainHand.getItem()) && !itemInOffHand.isEmpty()) {
            if (NMSWrapper.hasCustomModel(itemInMainHand)) {
                return false;
            }
            this.target = entity.getMainArm() == HumanoidArm.RIGHT ? this.right : this.left;
            return true;
        }
        if (this.compatibleMaps.contains(itemInOffHand.getItem()) && !itemInOffHand.isEmpty()) {
            if (NMSWrapper.hasCustomModel(itemInOffHand)) {
                return false;
            }
            this.target = entity.getMainArm() == HumanoidArm.RIGHT ? this.left : this.right;
            return true;
        }
        return false;
    }

    @Override
    public BodyPart[] getBodyParts(AbstractClientPlayer entity, PlayerData data) {
        return this.target;
    }

    @Override
    public int getPriority(AbstractClientPlayer entity, PlayerData data) {
        return 300;
    }

    @Override
    public void apply(AbstractClientPlayer entity, PlayerData data, PlayerModel model, BodyPart part, float delta, float tickCounter) {
        HumanoidArm arm;
        HumanoidArm humanoidArm = arm = part == BodyPart.LEFT_ARM ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
        if (this.target == this.bothHands) {
            AnimationUtil.applyArmTransforms(model, arm, -Mth.lerp((float)(-1.0f * (EntityUtil.getXRot((Entity)entity) - 90.0f) / 180.0f), (float)0.7f, (float)0.9f), Mth.lerp((float)(-1.0f * (EntityUtil.getXRot((Entity)entity) - 90.0f) / 180.0f), (float)-0.3f, (float)-0.2f), 0.3f);
            return;
        }
        AnimationUtil.applyArmTransforms(model, arm, -Mth.lerp((float)(-1.0f * (EntityUtil.getXRot((Entity)entity) - 90.0f) / 180.0f), (float)0.5f, (float)1.5f), 0.0f, 0.3f);
    }
}

