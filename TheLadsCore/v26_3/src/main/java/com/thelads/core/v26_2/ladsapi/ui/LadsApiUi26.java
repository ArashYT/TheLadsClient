package com.thelads.core.v26_2.ladsapi.ui;

import com.thelads.core.client.ladsapi.LadsApiClient;
import com.thelads.core.client.ladsapi.minigame.Caps;
import com.thelads.core.client.ladsapi.minigame.Hooks;
import com.thelads.core.client.ladsapi.minigame.LockState.Lock;
import com.thelads.core.client.ladsapi.minigame.Minigame;
import com.thelads.core.config.SessionProfiles;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The input and 2D half of the minigame API on 26.3 (Lads API sections 8-23): the per-tick plumbing, lock, server action keys,
 * dialogs, audio, profile switch, input device and rumble. The HUD widgets and the run timer are common HUD elements
 * (registered with the HUD). Each cap is declared only after its hook is installed.
 */
public final class LadsApiUi26 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static Audio26 audio;
    private static ServerKeys26 keys;

    private LadsApiUi26() {}

    public static void register() {
        Pad26 pad = Pad26.get();
        boolean padOk = Pad26.init();
        audio = new Audio26();
        keys = new ServerKeys26();
        Hooks.input = pad;
        Hooks.sound = audio;
        Hooks.profiles = new SessionProfiles();
        Hooks.keys = keys;
        if (padOk) Hooks.rumble = pad;
        DialogUi26.register();
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            try {
                pad.tick();
                Minigame.tick();
                LadsApiClient.pollInput();
                keys.tick();
                audio.tick();
                DialogUi26.tick();
            } catch (RuntimeException e) {
                LOGGER.warn("Lads API client tick failed", e);
            }
        });
        for (String cap : new String[] {Caps.LOCK, Caps.ACTION, Caps.DIALOG, Caps.HUD, Caps.RUN, Caps.PROFILE, Caps.INPUT, Caps.AUDIO}) {
            LadsApiClient.capability(cap);
        }
        if (padOk) LadsApiClient.capability(Caps.RUMBLE);
        LOGGER.info("Lads API 26.3 input/2D layer ready (gamepad {}).", padOk ? "available" : "unavailable");
    }

    /** Lock: whether the key reads as released now. Only ever removes input; the pause key, chat and the rest are not mapped. */
    public static boolean locksKey(KeyMapping k) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.screen() != null || mc.options == null) return false;
        if (!Minigame.lock.any(Minigame.now())) return false;
        Options o = mc.options;
        if (k == o.keyUp || k == o.keyDown || k == o.keyLeft || k == o.keyRight) return Minigame.locked(Lock.MOVE);
        if (k == o.keyJump) return Minigame.locked(Lock.JUMP);
        if (k == o.keyShift) return Minigame.locked(Lock.SNEAK);
        if (k == o.keySprint) return Minigame.locked(Lock.SPRINT);
        if (k == o.keyAttack) return Minigame.locked(Lock.ATTACK);
        if (k == o.keyUse) return Minigame.locked(Lock.USE);
        if (k == o.keyDrop) return Minigame.locked(Lock.DROP);
        if (k == o.keyInventory) return Minigame.locked(Lock.INVENTORY);
        if (k == o.keySwapOffhand) return Minigame.locked(Lock.HOTBAR);
        for (KeyMapping slot : o.keyHotbarSlots) if (k == slot) return Minigame.locked(Lock.HOTBAR);
        return false;
    }
}
