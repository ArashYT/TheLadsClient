package com.thelads.core.modules;

import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.client.PvpVisuals;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;

/**
 * Hitboxes (Lunar's Hitbox, Badlion's Hitboxes, OneClient's PolyHitbox): Minecraft's F3+B entity boxes, shown while the module is
 * on, in your colours and line width, per kind of entity, with the eye height (red) and look vector (blue). A mob or player in its
 * hurt cooldown (no hit lands) shows the Hurt Color. Through Walls draws the boxes over blocks: it shows what the vanilla client
 * would not, so it is a risky part (HypixelSafetyManager, {@link #THROUGH_WALLS}): only in singleplayer, LAN and trusted servers.
 */
public final class HitboxesModule extends Module {
    public static final String NAME = "Hitboxes";
    /** The server rules' name for Through Walls. */
    public static final String THROUGH_WALLS = "HitboxesThroughWalls";
    public static final int PLAYER = 1, MOB = 2, ITEM = 4, PROJECTILE = 8, OTHER = 16;

    public final ColorOption boxColor = addOption(new ColorOption("Box Color", false, 0xFFFFFFFF));
    public final ColorOption hurtColor = addOption(new ColorOption("Hurt Color", false, 0xFFFF5555));
    public final BoolOption eyeHeight = addOption(new BoolOption("Eye Height", true));
    public final BoolOption lookVector = addOption(new BoolOption("Look Vector", true));
    public final SliderOption width = addOption(new SliderOption("Line Width", 2, 0.5, 10, 0.5));
    public final BoolOption throughWalls = addOption(new BoolOption("Through Walls", false));
    public final BoolOption players = addOption(new BoolOption("Players", true));
    public final BoolOption mobs = addOption(new BoolOption("Mobs", true));
    public final BoolOption items = addOption(new BoolOption("Items", true));
    public final BoolOption projectiles = addOption(new BoolOption("Projectiles", true));
    public final BoolOption others = addOption(new BoolOption("Other", true));

    /** Cached each tick; {@code walls} only where the server rules allow it. */
    public boolean on, eyes, look, walls;
    public int boxArgb = -1, hurtArgb = -1, kinds;
    public float lineWidth = 2;

    public HitboxesModule() {
        super(NAME, "Entity hitboxes (F3+B) in your colours, with the eye height, look vector and a hurt colour. "
            + "Through Walls works only in singleplayer, LAN and trusted servers.");
    }

    public void refresh() {
        on = isEnabled();
        boxArgb = PvpVisuals.argb(boxColor);
        hurtArgb = PvpVisuals.argb(hurtColor);
        eyes = eyeHeight.get();
        look = lookVector.get();
        lineWidth = (float) width.getValue();
        walls = throughWalls.get() && !HypixelSafetyManager.isDisallowed(THROUGH_WALLS);
        kinds = (players.get() ? PLAYER : 0) | (mobs.get() ? MOB : 0) | (items.get() ? ITEM : 0) | (projectiles.get() ? PROJECTILE : 0)
            | (others.get() ? OTHER : 0);
    }

    /** The module shows boxes of this kind ({@link #PLAYER} ... {@link #OTHER}). */
    public boolean shows(int kind) {
        return on && (kinds & kind) != 0;
    }

    /** The box's colour: the Hurt Color while a living entity's hurt animation runs (its hit cooldown). */
    public int color(boolean hurt) {
        return hurt ? hurtArgb : boxArgb;
    }
}
