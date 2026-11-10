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

    /** The entity this class put the camera on, so it only ever takes back its own camera. */
    private static Entity held;

    private Spectate26() {}

    public static void register() {
        KeyMappingHelper.registerKeyMapping(NEXT);
        KeyMappingHelper.registerKeyMapping(PREVIOUS);
        ClientTickEvents.START_CLIENT_TICK.register(Spectate26::input);
        ClientTickEvents.END_CLIENT_TICK.register(Spectate26::camera);
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
        if (step != 0 && state.on() && mc.player != null && mc.gui.screen() == null) LadsApiClient.spectateRequest(step > 0);
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
        Entity target = state.pov() ? mc.level.getEntity(state.target) : null;
        if (target != null && target != mc.player) {
            if (mc.getCameraEntity() != target) mc.setCameraEntity(target);
            held = target;
        } else if (held != null && !state.pov()) {
            if (mc.getCameraEntity() == held) mc.setCameraEntity(mc.player);
            held = null;
        }
    }

    /** Who is watched and the keys that switch, above the hotbar. Drawn only while the server says we spectate. */
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
        int x = (graphics.guiWidth() - width) / 2, y = graphics.guiHeight() - 96;
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
