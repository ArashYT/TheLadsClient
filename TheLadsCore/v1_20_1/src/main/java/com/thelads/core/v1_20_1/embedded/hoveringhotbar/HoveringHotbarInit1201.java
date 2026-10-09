// Adapted from Hovering Hotbar 26.3.0 by Fuzs (MPL-2.0); modified by The Lads: Puzzles Lib's key and tick helpers are replaced by
// the Fabric API calls Puzzles Lib makes on Fabric; ported to Minecraft 1.20.1 from the Lads 26.3 embedded copy.
package com.thelads.core.v1_20_1.embedded.hoveringhotbar;

import com.mojang.blaze3d.platform.InputConstants;
import com.thelads.core.v1_20_1.compat.ModPresence1201;
import com.thelads.core.v1_20_1.perf.SmallModsProbe1201;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Entry point called by EmbeddedMods1201 while the original Hovering Hotbar is not installed. */
public final class HoveringHotbarInit1201 {
    private static final String KEY_CATEGORY = "key.category.hoveringhotbar.main";
    public static KeyMapping moveHotbarUp;
    public static KeyMapping moveHotbarDown;

    private HoveringHotbarInit1201() {}

    public static void init() {
        HoveringHotbar.CONFIG.load();
        ClientTickEvents.END_CLIENT_TICK.register(HoveringHotbar.CONFIG::onEndClientTick);
        moveHotbarUp = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.hoveringhotbar.move_hotbar_up", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, KEY_CATEGORY));
        moveHotbarDown = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.hoveringhotbar.move_hotbar_down", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, KEY_CATEGORY));
        ClientTickEvents.START_CLIENT_TICK.register((Minecraft minecraft) -> {
            if (minecraft.player != null) {
                while (moveHotbarUp.consumeClick()) {
                    HoveringHotbar.CONFIG.updateHotbarOffset(minecraft.getWindow().getGuiScaledHeight(), true);
                }
                while (moveHotbarDown.consumeClick()) {
                    HoveringHotbar.CONFIG.updateHotbarOffset(minecraft.getWindow().getGuiScaledHeight(), false);
                }
            }
        });
        SmallModsProbe1201.runOnce("hoveringhotbar", true, HoveringHotbarInit1201::probe);
    }

    /**
     * Counts how often the hooked draw methods ran while the real HUD was drawn: the hotbar must have been drawn through the hook with
     * the configured translation, and a chat line added now must be laid out through the chat hook. The experience and status counters
     * only grow in survival; on Forge they show whether its gui draws those through Gui's own methods (they stay 0 if it does not).
     */
    private static void probe(Minecraft client) {
        client.gui.getChat().addMessage(Component.literal("PERF-PROBE hoveringhotbar: chat line"));
        int offset = HoveringHotbar.CONFIG.getHotbarOffset();
        boolean onForge = ModPresence1201.onForge();
        // the experience bar and the health row are only drawn in survival, so only then are their hooks expected to have run
        boolean survival = client.gameMode != null && client.gameMode.canHurtPlayer();
        boolean experience = client.gameMode != null && client.gameMode.hasExperience() && client.player.jumpableVehicle() == null;
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(2000L);
            } catch (InterruptedException ignored) {
                return;
            }
            boolean hotbar = HoveringHotbar.HOTBAR_DRAWS.get() > 0;
            boolean translated = offset == 0 || Math.abs(HoveringHotbar.lastHotbarTranslateY() + offset) < 0.001F;
            boolean chat = HoveringHotbar.CHAT_LINES.get() > 0;
            // On plain Fabric every hooked method must have run; on Forge the gui draws some rows itself, so those counters are only reported.
            boolean rows = onForge || ((!survival || HoveringHotbar.STATUS_DRAWS.get() > 0) && (!experience || HoveringHotbar.EXPERIENCE_DRAWS.get() > 0));
            SmallModsProbe1201.result("hoveringhotbar", hotbar && translated && chat && rows,
                "offset=" + offset + " hotbarDraws=" + HoveringHotbar.HOTBAR_DRAWS.get() + " hotbarTranslateY=" + HoveringHotbar.lastHotbarTranslateY()
                    + " chatLinesLaidOut=" + HoveringHotbar.CHAT_LINES.get() + " experienceBarDraws=" + HoveringHotbar.EXPERIENCE_DRAWS.get()
                    + " statusRowDraws=" + HoveringHotbar.STATUS_DRAWS.get() + " itemNameLines=" + HoveringHotbar.ITEM_NAME_LINES.get()
                    + " survival=" + survival + " onForge=" + onForge + " moveExperienceAboveBar=" + HoveringHotbar.CONFIG.moveExperienceAboveBar);
        }, "HoveringHotbar-probe");
        thread.setDaemon(true);
        thread.start();
    }
}
