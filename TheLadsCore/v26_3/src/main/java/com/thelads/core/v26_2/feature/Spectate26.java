package com.thelads.core.v26_2.feature;

import com.mojang.blaze3d.platform.InputConstants;
import com.thelads.core.client.ladsapi.LadsApiClient;
import com.thelads.core.client.ladsapi.SpectateState;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The client's side of a server's spectator mode (LadsClientAPI PROTOCOL.md section 7). The server says whom we watch; this
 * holds the camera on that player, keeps our own clicks and hotbar keys out of the mirrored view, draws who is watched and
 * asks the server for the next or previous player on a key press. Without a state from the server nothing here runs, and the
 * server's own camera packets work as before ({@link PovCamera}).
 */
public final class Spectate26 {
    public static final KeyMapping NEXT = new KeyMapping("key.theladscore.spectate_next",
        InputConstants.Type.KEYBOARD, InputConstants.KEY_RIGHT, NativeKeyBindings.CATEGORY);
    public static final KeyMapping PREVIOUS = new KeyMapping("key.theladscore.spectate_previous",
        InputConstants.Type.KEYBOARD, InputConstants.KEY_LEFT, NativeKeyBindings.CATEGORY);

    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");

    /** The entity this class put the camera on, so it only ever takes back its own camera. */
    private static Entity held;
    /** The last state written to the log. */
    private static SpectateState logged = SpectateState.active();

    private Spectate26() {}

    public static void register() {
        KeyMappingHelper.registerKeyMapping(NEXT);
        KeyMappingHelper.registerKeyMapping(PREVIOUS);
        ClientTickEvents.START_CLIENT_TICK.register(Spectate26::input);
        ClientTickEvents.END_CLIENT_TICK.register(Spectate26::camera);
        LadsApiClient.capability("spectate"); // the server may now send the camera packet and leave the camera to us
    }

    /** POV with the camera on the target: our clicks, hotbar keys and wheel would only disturb the mirrored hotbar. */
    public static boolean locksInput() {
        return SpectateState.active().pov() && PovCamera.active();
    }

    /** Before the game reads its keys: in POV the mouse buttons pick the next and previous player and reach nothing else. */
    private static void input(Minecraft mc) {
        SpectateState state = SpectateState.active();
        int step = drain(NEXT) - drain(PREVIOUS);
        if (mc.player != null && locksInput()) {
            Options options = mc.options;
            step += drain(options.keyAttack) - drain(options.keyUse);
            drain(options.keyPickItem);
            drain(options.keyDrop);
            drain(options.keySwapOffhand);
            for (KeyMapping slot : options.keyHotbarSlots) drain(slot);
            options.keyAttack.setDown(false);
            options.keyUse.setDown(false);
        }
        if (step != 0 && state.on() && mc.player != null && mc.gui.screen() == null && LadsApiClient.spectateRequest(step > 0)) {
            LOGGER.info("Lads spectate: asked the server for the {} player", step > 0 ? "next" : "previous");
        }
    }

    private static int drain(KeyMapping key) {
        int clicks = 0;
        while (key.consumeClick()) clicks++;
        return clicks;
    }

    /**
     * The server's camera packet is lost when the target is not in our world yet (another arena, a respawn), so the server
     * repeats it; knowing the target, the camera goes there in the tick the player appears.
     */
    private static void camera(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            held = null;
            return;
        }
        SpectateState state = SpectateState.active();
        if (state != logged) {
            logged = state;
            LOGGER.info("Lads spectate: server says {}{}", state.mode, state.canSwitch ? ", switching offered" : "");
        }
        Entity target = state.pov() ? mc.level.getEntity(state.target) : null;
        if (target != null && target != mc.player) {
            if (mc.getCameraEntity() != target) mc.setCameraEntity(target);
            if (held != target) LOGGER.info("Lads spectate: camera on {}", target.getName().getString());
            held = target;
        } else if (held != null && !state.pov()) {
            if (mc.getCameraEntity() == held) mc.setCameraEntity(mc.player);
            held = null;
            LOGGER.info("Lads spectate: camera back on the player");
        }
    }

    /** The boss bars drawn at the top (a spectator gets the watched player's cooldown bars there). */
    private static int bossBars(Minecraft mc) {
        try {
            var events = ((com.thelads.core.v26_2.mixin.BossBarAccessor) mc.gui.hud.getBossOverlay()).ladsEvents();
            return events == null ? 0 : events.size();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    /**
     * Who is watched and the keys that switch, at the top under the boss bars: above the hotbar it sat on the chat lines.
     * Drawn only while the server says we spectate.
     */
    public static void render(GuiGraphicsExtractor graphics) {
        Minecraft mc = Minecraft.getInstance();
        SpectateState state = SpectateState.active();
        if (!state.on() || mc.player == null || mc.gui.hud.isHidden()) return;

        String label = "SPECTATING";
        String name = state.name;
        if (!state.pov()) {
            name = "Free flight";
        } else if (name.isEmpty()) {
            Entity target = mc.level == null ? null : mc.level.getEntity(state.target);
            name = target == null ? "..." : target.getName().getString();
        }
        int labelWidth = mc.font.width(label), width = labelWidth + 6 + mc.font.width(name) + 16;
        // Vanilla draws a boss bar every 19 px from y 12, its name 9 px above it, until a third of the screen is used.
        int x = (graphics.guiWidth() - width) / 2, y = Math.min(4 + 19 * bossBars(mc), graphics.guiHeight() / 3 + 4);
        graphics.fill(x, y, x + width, y + 14, 0x99000000);
        graphics.fill(x, y, x + 2, y + 14, 0xFFE0443C);
        graphics.text(mc.font, label, x + 8, y + 3, 0xFFB8B8B8, false);
        graphics.text(mc.font, name, x + 14 + labelWidth, y + 3, 0xFFFFFFFF, false);

        if (!state.canSwitch) return;
        String hint = locksInput()
            ? key(mc.options.keyAttack) + " next   " + key(mc.options.keyUse) + " previous"
            : key(NEXT) + " next   " + key(PREVIOUS) + " previous";
        graphics.text(mc.font, hint, (graphics.guiWidth() - mc.font.width(hint)) / 2, y + 17, 0xC0FFFFFF, true);
    }

    private static String key(KeyMapping mapping) {
        return mapping.getTranslatedKeyMessage().getString();
    }
}
