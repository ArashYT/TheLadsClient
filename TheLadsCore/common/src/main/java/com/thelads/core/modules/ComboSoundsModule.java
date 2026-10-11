package com.thelads.core.modules;

import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.sound.CustomSounds;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.IntTextOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;
import com.thelads.core.config.SoundOption;

/**
 * A sound for each hit you land in a row without taking damage. A hit lands when the server hurts the entity you just attacked
 * (its hurt status on 1.8.9, its damage event on 1.20.1/26.x), so swings and hits into invulnerability don't count. The combo
 * ends when you take damage or after Combo Reset seconds without a hit. Each version only forwards attacks and hurts here.
 */
public class ComboSoundsModule extends Module {
    public static final String NAME = "Combo Sounds";
    /** The server's hurt answers your attack within this (ping included); a hurt later is someone else's hit. */
    static final long CONFIRM_NANOS = 1_000_000_000L;
    public final BoolOption players = new BoolOption("Players", true);
    public final BoolOption mobs = new BoolOption("Mobs", false);
    public final SliderOption volume = new SliderOption("Volume", .6, 0, 1, .05);
    public final IntTextOption reset = new IntTextOption("Combo Reset (seconds)", 2, 1, 30);
    /** Combo 1 to 5; steps past 5 use Combo 5. Empty: the built-in pling, higher each step. */
    public final SoundOption[] steps = new SoundOption[5];
    private int target = -1, combo;
    private long attackedAt, lastHit;

    public ComboSoundsModule() {
        super(NAME, "A sound for every hit you land in a row without taking damage, rising with the combo. Upload your own MP3 "
            + "or WAV for hits 1 to 5.");
        addOption(players);
        addOption(mobs);
        addOption(volume);
        addOption(reset);
        for (int i = 0; i < steps.length; i++) steps[i] = addOption(new SoundOption("Combo " + (i + 1)));
    }

    /** Your attack on a living entity (a player or not). */
    public void attacked(int entityId, boolean player, long now) {
        if (player ? players.get() : mobs.get()) {
            target = entityId;
            attackedAt = now;
        }
    }

    /** An entity was hurt ({@code self}: you, which ends the combo). Returns the combo step your landed hit makes, else 0. */
    public int hurt(int entityId, boolean self, long now) {
        if (self) {
            combo = 0;
            return 0;
        }
        if (entityId != target || now - attackedAt > CONFIRM_NANOS) return 0;
        target = -1;
        combo = combo > 0 && now - lastHit <= reset.get() * 1_000_000_000L ? combo + 1 : 1;
        lastHit = now;
        return combo;
    }

    public SoundOption slot(int step) {
        return steps[Math.max(1, Math.min(steps.length, step)) - 1];
    }

    /** The built-in pling's pitch for a step: up a little each hit, to the highest Minecraft plays. */
    public static float pitch(int step) {
        return Math.min(2f, .7f + .15f * (Math.max(1, step) - 1));
    }

    /** Plays a step: its upload, else the built-in pling at the step's pitch. */
    public void play(int step) {
        if (volume.getValue() <= 0) return;
        SoundOption slot = slot(step);
        if (slot.isSet()) CustomSounds.play(slot.get(), volume.getValue());
        else LadsGameBridge.get().playComboSound(pitch(step), (float) volume.getValue());
    }

    private static ComboSoundsModule active() {
        return ModuleManager.getInstance().getModule(NAME) instanceof ComboSoundsModule module && module.isEnabled() ? module : null;
    }

    /** Each version's attack hook (client thread). */
    public static void onAttack(int entityId, boolean player) {
        ComboSoundsModule module = active();
        if (module != null) module.attacked(entityId, player, System.nanoTime());
    }

    /** Each version's hurt hook (client thread): plays the step a landed hit makes. */
    public static void onHurt(int entityId, boolean self) {
        ComboSoundsModule module = active();
        if (module == null) return;
        int step = module.hurt(entityId, self, System.nanoTime());
        if (step > 0) module.play(step);
    }
}
