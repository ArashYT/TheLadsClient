package com.thelads.smp.features.arise.nb3.obscuretooltips;

import com.thelads.smp.features.arise.nb3.AriseNb3Config;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

/**
 * Obscure Tooltips Feature:
 * Renders rotating 3D miniatures of weapons, tools, and wearable armor inside item tooltips.
 */
public final class ObscureTooltipsFeature {
    private ObscureTooltipsFeature() {}

    public static boolean shouldShowPreview(ItemStack stack) {
        if (!AriseNb3Config.get().obscureTooltips.enabled) return false;
        if (stack == null || stack.isEmpty()) return false;

        // In 26.3, wearable armor has DataComponents.EQUIPPABLE
        if (stack.has(DataComponents.EQUIPPABLE)) {
            return AriseNb3Config.get().obscureTooltips.displayArmorModels;
        }

        // Weapons and tools have WEAPON, TOOL, PIERCING_WEAPON, KINETIC_WEAPON, or MAX_DAMAGE
        if (stack.has(DataComponents.WEAPON)
                || stack.has(DataComponents.TOOL)
                || stack.has(DataComponents.PIERCING_WEAPON)
                || stack.has(DataComponents.KINETIC_WEAPON)
                || stack.has(DataComponents.MAX_DAMAGE)) {
            return AriseNb3Config.get().obscureTooltips.displayToolsModels;
        }

        return false;
    }
}
