/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.player.AbstractClientPlayer
 *  net.minecraft.core.component.DataComponents
 *  net.minecraft.world.InteractionHand
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.HumanoidArm
 *  net.minecraft.world.item.ItemStack
 */
package dev.tr7zw.notenoughanimations.util;

import dev.tr7zw.notenoughanimations.versionless.animations.BodyPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

public class NMSWrapper {
    public static boolean hasCustomModel(ItemStack itemStack) {
        return itemStack.getComponents().has(DataComponents.CUSTOM_MODEL_DATA);
    }

    public static boolean onGround(Entity entity) {
        return entity.onGround();
    }

    public static BodyPart getArm(AbstractClientPlayer entity, InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND) {
            return entity.getMainArm() == HumanoidArm.RIGHT ? BodyPart.RIGHT_ARM : BodyPart.LEFT_ARM;
        }
        return entity.getMainArm() == HumanoidArm.RIGHT ? BodyPart.LEFT_ARM : BodyPart.RIGHT_ARM;
    }
}

