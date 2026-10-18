package com.thelads.smp.features.arise.nb3.holdmyitems;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.thelads.smp.features.arise.nb3.AriseNb3Config;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;

/**
 * Hold My Items Feature:
 * Renders player arms in a custom two-handed holding pose in first person when holding bulky items or blocks.
 */
public final class HoldMyItemsFeature {
    private HoldMyItemsFeature() {}

    private static final java.util.Set<String> NON_BULKY_KEYWORDS = java.util.Set.of(
            "torch", "button", "lever", "flower", "sapling", "redstone", "rail",
            "pressure_plate", "tripwire", "carpet", "lantern", "candle", "mushroom",
            "fern", "grass", "tulip", "orchid", "allium", "poppy", "dandelion",
            "daisy", "cornflower", "lily", "vine", "hanging_roots", "spore_blossom"
    );

    public static boolean isBulkyItem(ItemStack stack) {
        if (!AriseNb3Config.get().holdMyItems.enabled) return false;
        if (stack == null || stack.isEmpty()) return false;

        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id != null && AriseNb3Config.get().holdMyItems.excludedItemIds.contains(id.toString())) {
            return false;
        }

        // Exclude lightweight non-bulky blocks (torches, buttons, levers, flowers, etc.)
        if (stack.getItem() instanceof BlockItem) {
            if (id != null) {
                String path = id.getPath();
                for (String keyword : NON_BULKY_KEYWORDS) {
                    if (path.contains(keyword)) {
                        return false;
                    }
                }
            }
            return true;
        }
        return stack.getItem() instanceof MaceItem;
    }

    /**
     * Applies two-handed carrying transformation to the first-person hand model.
     */
    @net.fabricmc.api.Environment(net.fabricmc.api.EnvType.CLIENT)
    public static void applyTwoHandedTransform(PoseStack poseStack, HumanoidArm arm, float equipProgress) {
        if (!AriseNb3Config.get().holdMyItems.enabled) return;

        int sign = (arm == HumanoidArm.RIGHT) ? 1 : -1;
        float xOffset = AriseNb3Config.get().holdMyItems.viewmodelXOffset;
        float yOffset = AriseNb3Config.get().holdMyItems.viewmodelYOffset;
        float zOffset = AriseNb3Config.get().holdMyItems.viewmodelZOffset;

        // Angle hands forward and inward toward center
        poseStack.translate(sign * (0.12f + xOffset), -0.08f + yOffset, -0.15f + zOffset);
        poseStack.rotateDegrees(Axis.XP, -22.0f * (float) AriseNb3Config.get().holdMyItems.animationSpeed);
        poseStack.rotateDegrees(Axis.YP, sign * 18.0f);
        poseStack.rotateDegrees(Axis.ZP, sign * -12.0f);
    }
}
