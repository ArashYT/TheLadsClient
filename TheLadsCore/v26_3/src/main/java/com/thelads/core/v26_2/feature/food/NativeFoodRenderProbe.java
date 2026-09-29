package com.thelads.core.v26_2.feature.food;

import com.google.gson.JsonElement;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Option;
import com.thelads.core.v26_2.feature.NativeQualityOfLife;
import com.thelads.core.v26_2.feature.food.client.TooltipOverlayHandler.FoodOverlayTextComponent;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import org.slf4j.LoggerFactory;

/** Uses actual bound food components and GUI extraction without changing focus/desktop input. */
public final class NativeFoodRenderProbe {
    private static boolean done;
    private static int passed;
    public static void tick() {
        var mc = Minecraft.getInstance();
        if (done || !Boolean.getBoolean("thelads.verifyIntegrations") || !NativeFoodOverlay.active()
                || mc.player == null || mc.level == null) return;
        done = true;
        var module = NativeQualityOfLife.module("AppleSkin");
        boolean enabled = module.isEnabled(); long modified = module.getLastModified();
        Map<Option, JsonElement> preferences = new LinkedHashMap<>();
        module.getOptions().forEach(option -> preferences.put(option, option.save().deepCopy()));
        try {
            module.setEnabled(true);
            ((BoolOption) module.getOption("Food Tooltips")).set(true);
            ((BoolOption) module.getOption("Tooltips Always Visible")).set(true);
            FoodOverlayConfig.refresh();
            for (var item : java.util.List.of(Items.APPLE, Items.COOKED_BEEF, Items.ROTTEN_FLESH, Items.GOLDEN_APPLE)) {
                var lines = new ItemStack(item).getTooltipLines(Item.TooltipContext.EMPTY, null, TooltipFlag.NORMAL);
                var overlays = lines.stream().filter(FoodOverlayTextComponent.class::isInstance).toList();
                check(overlays.size() == 1, "one actual item-tooltip overlay for " + item + ": " + lines.stream().map(net.minecraft.network.chat.Component::getString).toList());
                var overlay = (FoodOverlayTextComponent) overlays.getFirst();
                var tooltip = ClientTooltipComponent.create(overlay.getVisualOrderText());
                check(tooltip == overlay.foodOverlay && tooltip.getWidth(mc.font) > 0 && tooltip.getHeight(mc.font) == 20,
                        "transformed text factory returns native food component");
                var state = new GuiRenderState();
                var graphics = new GuiGraphicsExtractor(mc, state, 0, 0);
                var pose = new org.joml.Matrix3x2f(graphics.pose());
                tooltip.extractImage(mc.font, 20, 20, 200, 200, graphics);
                int[] count = {0}; state.forEachElement(element -> count[0]++, GuiRenderState.TraverseRange.ALL);
                check(count[0] > 0 && graphics.pose().equals(pose), "real food sprite extraction restores GUI pose");
                if (item == Items.APPLE) NativeFoodJeiProbe.run(overlay);
            }
            check(mc.getResourceManager().getResource(Identifier.fromNamespaceAndPath("theladscore", "textures/food/icons.png")).isPresent(), "native food sprites packaged");
            module.setEnabled(false); FoodOverlayConfig.refresh();
            check(new ItemStack(Items.APPLE).getTooltipLines(Item.TooltipContext.EMPTY, null, TooltipFlag.NORMAL).stream()
                    .noneMatch(FoodOverlayTextComponent.class::isInstance), "master disable restores ordinary tooltip");
            LoggerFactory.getLogger("TheLadsCore").info("Lads food render probe END: {} passed, 0 failed", passed);
        } catch (Throwable failure) {
            LoggerFactory.getLogger("TheLadsCore").error("Lads native feature probe FAILED: food extraction after {} checks", passed, failure);
        } finally {
            preferences.forEach(Option::load); module.setEnabled(enabled); module.setLastModified(modified); FoodOverlayConfig.refresh();
        }
    }
    private static void check(boolean value, String message) { if (!value) throw new IllegalStateException(message); passed++; }
}
