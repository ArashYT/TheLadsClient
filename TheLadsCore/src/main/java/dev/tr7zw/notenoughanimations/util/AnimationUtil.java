/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  dev.tr7zw.transition.mc.GeneralUtil
 *  dev.tr7zw.transition.mc.ItemUtil
 *  net.minecraft.client.model.HumanoidModel$ArmPose
 *  net.minecraft.client.model.geom.ModelPart
 *  net.minecraft.client.model.player.PlayerModel
 *  net.minecraft.client.player.AbstractClientPlayer
 *  net.minecraft.world.InteractionHand
 *  net.minecraft.world.entity.HumanoidArm
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.item.CrossbowItem
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.ItemUseAnimation
 */
package dev.tr7zw.notenoughanimations.util;

import dev.tr7zw.notenoughanimations.versionless.NEABaseMod;
import dev.tr7zw.notenoughanimations.versionless.animations.BodyPart;
import dev.tr7zw.transition.mc.GeneralUtil;
import dev.tr7zw.transition.mc.ItemUtil;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;

public class AnimationUtil {
    private static Item crossbow = ItemUtil.getItem((Identifier)GeneralUtil.getResourceLocation((String)"minecraft", (String)"crossbow"));

    public static Set<Item> parseItemList(Collection<String> list) {
        HashSet<Item> items = new HashSet<Item>();
        Item invalid = ItemUtil.getItem((Identifier)GeneralUtil.getResourceLocation((String)"minecraft", (String)"air"));
        for (String itemId : list) {
            try {
                String[] parts = itemId.split(":");
                if (parts.length != 2) {
                    NEABaseMod.LOGGER.info("Invalid item ID format (expected namespace:path): " + itemId);
                    continue;
                }
                Item item = ItemUtil.getItem((Identifier)GeneralUtil.getResourceLocation((String)parts[0], (String)parts[1]));
                if (invalid == item) continue;
                items.add(item);
            }
            catch (Exception ex) {
                NEABaseMod.LOGGER.info("Unknown item to add to the list: " + itemId);
            }
        }
        return items;
    }

    public static boolean isUsingBothHands(HumanoidModel.ArmPose pose) {
        return pose == HumanoidModel.ArmPose.BOW_AND_ARROW || pose == HumanoidModel.ArmPose.CROSSBOW_CHARGE || pose == HumanoidModel.ArmPose.CROSSBOW_HOLD;
    }

    public static boolean isSwingingArm(AbstractClientPlayer player, BodyPart arm) {
        if (!player.swinging) {
            return false;
        }
        if (arm == BodyPart.LEFT_ARM) {
            return player.getMainArm() == HumanoidArm.LEFT && player.swingingArm == InteractionHand.MAIN_HAND || player.getMainArm() == HumanoidArm.RIGHT && player.swingingArm == InteractionHand.OFF_HAND;
        }
        return player.getMainArm() == HumanoidArm.RIGHT && player.swingingArm == InteractionHand.MAIN_HAND || player.getMainArm() == HumanoidArm.LEFT && player.swingingArm == InteractionHand.OFF_HAND;
    }

    public static HumanoidModel.ArmPose getArmPose(AbstractClientPlayer abstractClientPlayerEntity, InteractionHand hand) {
        ItemStack itemStack = abstractClientPlayerEntity.getItemInHand(hand);
        if (itemStack.isEmpty()) {
            return HumanoidModel.ArmPose.EMPTY;
        }
        if (abstractClientPlayerEntity.getUsedItemHand() == hand && abstractClientPlayerEntity.getUseItemRemainingTicks() > 0) {
            ItemUseAnimation useAction = itemStack.getUseAnimation();
            if (useAction == ItemUseAnimation.BLOCK) {
                return HumanoidModel.ArmPose.BLOCK;
            }
            if (useAction == ItemUseAnimation.BOW) {
                return HumanoidModel.ArmPose.BOW_AND_ARROW;
            }
            if (useAction == ItemUseAnimation.TRIDENT) {
                return HumanoidModel.ArmPose.THROW_TRIDENT;
            }
            if (useAction == ItemUseAnimation.SPEAR) {
                return HumanoidModel.ArmPose.SPEAR;
            }
            if (useAction == ItemUseAnimation.SPYGLASS) {
                return HumanoidModel.ArmPose.SPYGLASS;
            }
            if (useAction == ItemUseAnimation.CROSSBOW && hand.equals((Object)abstractClientPlayerEntity.getUsedItemHand())) {
                return HumanoidModel.ArmPose.CROSSBOW_CHARGE;
            }
        } else if (!abstractClientPlayerEntity.swinging && itemStack.getItem().equals(crossbow) && AnimationUtil.isChargedCrossbow(itemStack)) {
            return HumanoidModel.ArmPose.CROSSBOW_HOLD;
        }
        return HumanoidModel.ArmPose.ITEM;
    }

    public static boolean isChargedCrossbow(ItemStack item) {
        return CrossbowItem.isCharged((ItemStack)item);
    }

    public static void applyArmTransforms(PlayerModel model, HumanoidArm arm, float pitch, float yaw, float roll) {
        ModelPart part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        part.xRot = pitch;
        part.yRot = yaw;
        if (arm == HumanoidArm.LEFT) {
            part.yRot *= -1.0f;
        }
        part.zRot = roll;
        if (arm == HumanoidArm.LEFT) {
            part.zRot *= -1.0f;
        }
    }

    public static void applyTransforms(PlayerModel model, BodyPart bodyPart, float pitch, float yaw, float roll) {
        boolean mirror = false;
        ModelPart targetPart = null;
        switch (bodyPart) {
            case LEFT_ARM: {
                mirror = true;
                targetPart = model.leftArm;
                break;
            }
            case RIGHT_ARM: {
                targetPart = model.rightArm;
                break;
            }
            case LEFT_LEG: {
                mirror = true;
                targetPart = model.leftLeg;
                break;
            }
            case RIGHT_LEG: {
                targetPart = model.rightLeg;
                break;
            }
            default: {
                return;
            }
        }
        targetPart.xRot = pitch;
        targetPart.yRot = yaw;
        if (mirror) {
            targetPart.yRot *= -1.0f;
        }
        targetPart.zRot = roll;
        if (mirror) {
            targetPart.zRot *= -1.0f;
        }
    }

    public static void minMaxHeadRotation(Player livingEntity, PlayerModel model) {
        float value = AnimationUtil.legacyWrapDegrees(model.head.yRot);
        float min = AnimationUtil.legacyWrapDegrees(model.body.yRot - 1.5707964f);
        float max = AnimationUtil.legacyWrapDegrees(model.body.yRot + 1.5707964f);
        value = Math.min(value, max);
        value = Math.max(value, min);
        AnimationUtil.setHeadYRot(model, value);
    }

    public static void setHeadYRot(PlayerModel model, float value) {
        model.head.yRot = value;
    }

    public static float interpolateRotation(float start, float end, float amount) {
        float wrappedStart = AnimationUtil.wrapDegrees(start);
        float wrappedEnd = AnimationUtil.wrapDegrees(end);
        float diff = wrappedEnd - wrappedStart;
        if (diff > (float)Math.PI) {
            wrappedEnd -= (float)Math.PI * 2;
        } else if (diff < (float)(-Math.PI)) {
            wrappedEnd += (float)Math.PI * 2;
        }
        return AnimationUtil.wrapDegrees(wrappedStart + (wrappedEnd - wrappedStart) * amount);
    }

    public static float interpolateRotation2(float start, float end, float amount) {
        float wrappedStart = AnimationUtil.wrapDegrees2(start);
        float wrappedEnd = AnimationUtil.wrapDegrees2(end);
        float diff = wrappedEnd - wrappedStart;
        if (diff > 180.0f) {
            wrappedEnd -= 360.0f;
        } else if (diff < -180.0f) {
            wrappedEnd += 360.0f;
        }
        return AnimationUtil.wrapDegrees2(wrappedStart + (wrappedEnd - wrappedStart) * amount);
    }

    public static float lerpAngle(float delta, float start, float end) {
        float wrappedStart = AnimationUtil.wrapDegrees(start);
        float wrappedEnd = AnimationUtil.wrapDegrees(end);
        float difference = wrappedEnd - wrappedStart;
        float shortestPath = (difference + (float)Math.PI) % ((float)Math.PI * 2) - (float)Math.PI;
        return AnimationUtil.wrapDegrees(wrappedStart + shortestPath * delta);
    }

    public static float wrapDegrees(float angle) {
        return (angle + (float)Math.PI) % ((float)Math.PI * 2) - (float)Math.PI;
    }

    public static float wrapDegrees2(float angle) {
        float wrapped = (angle + 180.0f) % 360.0f;
        if (wrapped < 0.0f) {
            wrapped += 360.0f;
        }
        return wrapped - 180.0f;
    }

    public static float legacyWrapDegrees(float f) {
        float g = f % 6.283185f;
        if (g >= 3.1415925f) {
            g -= 6.283185f;
        }
        if (g < -3.1415925f) {
            g += 6.283185f;
        }
        return g;
    }
}

