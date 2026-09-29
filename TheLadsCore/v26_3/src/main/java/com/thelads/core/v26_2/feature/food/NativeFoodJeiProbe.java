package com.thelads.core.v26_2.feature.food;

import com.mojang.datafixers.util.Either;
import com.thelads.core.v26_2.feature.food.client.TooltipOverlayHandler.FoodOverlayTextComponent;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import org.slf4j.LoggerFactory;

/** Opt-in verification invokes the transformed, pinned JEI conversion shared by both render paths. */
public final class NativeFoodJeiProbe {
    private NativeFoodJeiProbe() {}

    public static void run(FoodOverlayTextComponent food) throws ReflectiveOperationException {
        if (!Boolean.getBoolean("thelads.verifyIntegrations") || !FabricLoader.getInstance().isModLoaded("jei")) return;
        Class<?> type = Class.forName("mezz.jei.fabric.platform.RenderHelper");
        var helper = type.getConstructor().newInstance();
        var method = type.getDeclaredMethod("createClientTooltipComponents", List.class, Font.class);
        method.setAccessible(true);
        var before = Component.literal("Food before");
        var after = Component.literal("Food after");
        // Existing non-text payload stays in its exact slot; input deliberately cannot be mutated.
        var existing = new net.minecraft.world.inventory.tooltip.BundleTooltip(net.minecraft.world.item.component.BundleContents.EMPTY);
        List<Either<FormattedText, TooltipComponent>> input = List.of(
                Either.left(before), Either.left(food), Either.right(existing), Either.left(after));
        var result = (List<?>) method.invoke(helper, input, Minecraft.getInstance().font);
        require(result.size() == 4, "mixed tooltip retains four components");
        require(result.get(1) == food.foodOverlay, "food remains a rendered component after actual JEI conversion");
        require(result.get(2) instanceof net.minecraft.client.gui.screens.inventory.tooltip.ClientBundleTooltip
                && input.get(2).right().orElseThrow() == existing, "existing bundle payload keeps position and source identity");
        require(input.get(1).left().orElseThrow() == food, "immutable source is unchanged");
        require(result.get(0) instanceof ClientTooltipComponent && result.get(3) instanceof ClientTooltipComponent,
                "surrounding text remains rendered text");
        var textOnly = (List<?>) method.invoke(helper, List.of(Either.left(before)), Minecraft.getInstance().font);
        require(textOnly.size() == 1 && textOnly.getFirst() instanceof ClientTooltipComponent,
                "ordinary tooltip path remains valid");
        LoggerFactory.getLogger("TheLadsCore").info("Lads food JEI probe END: 6 passed, 0 failed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Native food JEI: " + message);
    }
}
