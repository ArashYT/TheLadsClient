// Adapted from Hovering Hotbar 21.1.1 by Fuzs (MPL-2.0); modified by The Lads: Puzzles Lib's key and tick helpers are
// replaced by the Fabric API calls Puzzles Lib makes on Fabric. Its shared gui heights only fed other Puzzles Lib mods.
package com.thelads.core.v1_21_1.embedded.hoveringhotbar.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.thelads.core.v1_21_1.embedded.hoveringhotbar.HoveringHotbar;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

public class HoveringHotbarClient {
    public static final KeyMapping MOVE_HOTBAR_UP_KEY_MAPPING = new KeyMapping("key.move_hotbar_up",
            InputConstants.UNKNOWN.getValue(), "key.categories." + HoveringHotbar.MOD_ID);
    public static final KeyMapping MOVE_HOTBAR_DOWN_KEY_MAPPING = new KeyMapping("key.move_hotbar_down",
            InputConstants.UNKNOWN.getValue(), "key.categories." + HoveringHotbar.MOD_ID);

    public static void init() {
        HoveringHotbar.CONFIG.load();
        ClientTickEvents.END_CLIENT_TICK.register(HoveringHotbar.CONFIG::onEndClientTick);
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
}
