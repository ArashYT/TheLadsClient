// Adapted from Hovering Hotbar 21.11.0 by Fuzs (MPL-2.0); modified by The Lads: Puzzles Lib's key, tick, chat and gui
// layer helpers are replaced by the Fabric API calls Puzzles Lib makes on Fabric. The selection sprite is drawn one
// pixel taller by GuiMixin instead of a second sprite layer, so Core's SmoothHotbar animation moves one highlight.
package com.thelads.core.v1_21_11.embedded.hoveringhotbar.client;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.platform.InputConstants;
import com.thelads.core.v1_21_11.embedded.hoveringhotbar.HoveringHotbar;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudStatusBarHeightRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.util.List;

public class HoveringHotbarClient {
    private static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(HoveringHotbar.id("main"));
    public static final KeyMapping MOVE_HOTBAR_UP_KEY_MAPPING = new KeyMapping("key.hoveringhotbar.move_hotbar_up",
            InputConstants.UNKNOWN.getValue(), KEY_CATEGORY);
    public static final KeyMapping MOVE_HOTBAR_DOWN_KEY_MAPPING = new KeyMapping("key.hoveringhotbar.move_hotbar_down",
            InputConstants.UNKNOWN.getValue(), KEY_CATEGORY);
    public static final Identifier LEFT_HOTBAR_OFFSET_LOCATION = HoveringHotbar.id("left_hotbar_offset");
    public static final Identifier RIGHT_HOTBAR_OFFSET_LOCATION = HoveringHotbar.id("right_hotbar_offset");
    public static final List<Identifier> HOTBAR_GUI_LAYER_LOCATIONS = ImmutableList.of(VanillaHudElements.HOTBAR,
            VanillaHudElements.INFO_BAR,
            VanillaHudElements.EXPERIENCE_LEVEL);
    private static final HudElement EMPTY_LAYER = (guiGraphics, deltaTracker) -> {
        // NO-OP
    };

    public static void init() {
        HoveringHotbar.CONFIG.load();
        registerEventHandlers();
        onRegisterKeyMappings();
        onRegisterGuiLayers();
    }

    private static void registerEventHandlers() {
        ClientTickEvents.END_CLIENT_TICK.register(HoveringHotbar.CONFIG::onEndClientTick);
        HudElementRegistry.replaceElement(VanillaHudElements.CHAT, (HudElement layer) -> {
            return (guiGraphics, deltaTracker) -> {
                guiGraphics.pose().pushMatrix();
                guiGraphics.pose().translate(0.0F, -HoveringHotbar.CONFIG.getHotbarOffset());
                layer.render(guiGraphics, deltaTracker);
                guiGraphics.pose().popMatrix();
            };
        });
    }

    private static void onRegisterKeyMappings() {
        KeyBindingHelper.registerKeyBinding(MOVE_HOTBAR_UP_KEY_MAPPING);
        KeyBindingHelper.registerKeyBinding(MOVE_HOTBAR_DOWN_KEY_MAPPING);
        ClientTickEvents.START_CLIENT_TICK.register((Minecraft minecraft) -> {
            if (minecraft.player != null) {
                while (MOVE_HOTBAR_UP_KEY_MAPPING.consumeClick()) {
                    HoveringHotbar.CONFIG.updateHotbarOffset(minecraft.getWindow().getGuiScaledHeight(), true);
                }
                while (MOVE_HOTBAR_DOWN_KEY_MAPPING.consumeClick()) {
                    HoveringHotbar.CONFIG.updateHotbarOffset(minecraft.getWindow().getGuiScaledHeight(), false);
                }
            }
        });
    }

    private static void onRegisterGuiLayers() {
        // register fake layers, so we can have status bar height providers which run as early as possible
        // on Fabric this must be a status bar, all other gui layers are sorted afterward
        HudElementRegistry.attachElementBefore(VanillaHudElements.HEALTH_BAR, LEFT_HOTBAR_OFFSET_LOCATION, EMPTY_LAYER);
        HudElementRegistry.attachElementBefore(VanillaHudElements.MOUNT_HEALTH, RIGHT_HOTBAR_OFFSET_LOCATION, EMPTY_LAYER);
        HudStatusBarHeightRegistry.addLeft(LEFT_HOTBAR_OFFSET_LOCATION, player -> HoveringHotbar.CONFIG.getHotbarOffset());
        HudStatusBarHeightRegistry.addRight(RIGHT_HOTBAR_OFFSET_LOCATION, player -> HoveringHotbar.CONFIG.getHotbarOffset());
        // push the experience bar level text above the bar, similar to the legacy console edition
        HudElementRegistry.replaceElement(VanillaHudElements.EXPERIENCE_LEVEL, (HudElement layer) -> {
            return (guiGraphics, deltaTracker) -> {
                if (HoveringHotbar.CONFIG.moveExperienceAboveBar) {
                    guiGraphics.pose().pushMatrix();
                    guiGraphics.pose().translate(0.0F, -3.0F);
                    layer.render(guiGraphics, deltaTracker);
                    guiGraphics.pose().popMatrix();
                } else {
                    layer.render(guiGraphics, deltaTracker);
                }
            };
        });
        // run this as late as possible, so we can wrap the most possible layers
        ClientLifecycleEvents.CLIENT_STARTED.register((Minecraft minecraft) -> {
            for (Identifier identifier : HOTBAR_GUI_LAYER_LOCATIONS) {
                HudElementRegistry.replaceElement(identifier, HoveringHotbarClient::getLayerWithTranslation);
            }
            // our gui layer system does not support modded layers, so use the native event here
            for (Identifier identifier : HoveringHotbar.CONFIG.hotbarGuiLayers) {
                try {
                    HudElementRegistry.replaceElement(identifier, HoveringHotbarClient::getLayerWithTranslation);
                } catch (Exception exception) {
                    // NO-OP
                }
            }
        });
    }

    public static HudElement getLayerWithTranslation(HudElement layer) {
        return (guiGraphics, deltaTracker) -> {
            guiGraphics.pose().pushMatrix();
            guiGraphics.pose().translate(0.0F, -HoveringHotbar.CONFIG.getHotbarOffset());
            layer.render(guiGraphics, deltaTracker);
            guiGraphics.pose().popMatrix();
        };
    }
}
