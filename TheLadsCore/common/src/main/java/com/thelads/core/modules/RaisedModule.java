package com.thelads.core.modules;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;

/**
 * Raised: lifts the hotbar, everything drawn on it (hearts, hunger, armour, air, XP, held item name) and the action bar off the
 * bottom edge, as on Bedrock; lifts them further while chat is open so the chat box does not cover them; can lift chat too.
 * Each version moves those vanilla parts by these amounts (GUI pixels).
 */
public class RaisedModule extends Module {
    public static final String NAME = "Raised";
    /** The vanilla parts Raised also moves and scales one by one (OneClient's VanillaHUD), in the order of {@link #dx}, {@link #dy}, {@link #scale}. */
    public static final String[] PARTS = {"Hotbar", "Health", "Hunger", "Armor", "Air", "Mount"};
    public static final int HOTBAR = 0, HEALTH = 1, FOOD = 2, ARMOR = 3, AIR = 4, MOUNT = 5;

    private final SliderOption optHotbar;
    private final SliderOption optDistance;
    private final SliderOption optChat;
    private static volatile RaisedModule cachedInstance = null;

    public RaisedModule() {
        super(NAME, "Lifts the hotbar, the bars on it and the action bar off the bottom of the screen, and above the chat box while "
            + "chat is open (Distance). Chat can be raised too, and the hotbar, hearts, hunger, armour, air and mount hearts each moved and scaled.");
        addOption(optHotbar = new SliderOption("Hotbar", 2, 0, 50, 1));
        // Its 1.6.0 name and value, so saved settings carry over: the extra lift while chat is open.
        addOption(optDistance = new SliderOption("Distance", 14, 0, 50, 1));
        addOption(optChat = new SliderOption("Chat", 0, 0, 100, 1));
        for (int part = 0; part < PARTS.length; part++) {
            xs[part] = addOption(new SliderOption(PARTS[part] + " X Offset", 0, -300, 300, 1));
            if (part != HOTBAR) ys[part] = addOption(new SliderOption(PARTS[part] + " Y Offset", 0, -300, 300, 1)); // the hotbar's is the lift
            scales[part] = addOption(new SliderOption(PARTS[part] + " Scale", 100, 50, 200, 5).percent());
        }
        setEnabled(true);
    }

    /** How far the hotbar group moves up; 0 while the module is off. */
    public int hotbarLift(boolean chatOpen) {
        return isEnabled() ? optHotbar.getIntValue() + (chatOpen ? optDistance.getIntValue() : 0) : 0;
    }

    /** How far chat moves up; 0 while the module is off. */
    public int chatLift() {
        return isEnabled() ? optChat.getIntValue() : 0;
    }

    /**
     * Carries a 1.6.0 26.x layout (config/lads-raised.json) over: how far it moved the hotbar and chat up becomes Hotbar and Chat.
     * Its other layers (boss bar, scoreboard, effects...) have Lads HUD modules of their own.
     */
    public void adoptLayout(JsonObject layout) {
        if (getOption("Hotbar") instanceof SliderOption hotbar) hotbar.setValue(layoutLift(layout, "minecraft:hotbar", "bottom"));
        if (getOption("Chat") instanceof SliderOption chat) chat.setValue(layoutLift(layout, "minecraft:chat", "none"));
    }

    /** Upward pixels: the summed y offsets of the groups holding the layer, applied up for bottom anchors and down for top ones. */
    static int layoutLift(JsonObject layout, String layer, String defaultAnchor) {
        String anchor = defaultAnchor;
        if (layout.get("layers") instanceof JsonObject layers && layers.get(layer) instanceof JsonObject entry) {
            JsonElement name = entry.get("anchor");
            if (name != null && name.isJsonPrimitive()) anchor = name.getAsString();
        }
        anchor = anchor.toLowerCase(java.util.Locale.ROOT);
        int direction = anchor.startsWith("bottom") ? 1 : anchor.startsWith("top") ? -1 : 0, offset = 0;
        if (layout.get("groups") instanceof JsonObject groups)
            for (var group : groups.entrySet())
                if (group.getValue() instanceof JsonObject value && value.get("layers") instanceof JsonArray members
                    && members.contains(new JsonPrimitive(layer)) && value.get("offset") instanceof JsonObject move
                    && move.get("y") != null && move.get("y").isJsonPrimitive()) offset += move.get("y").getAsInt();
        return offset * direction;
    }

    /**
     * Cached each tick (refresh): how each vanilla part (HOTBAR to MOUNT) is moved (GUI pixels) and scaled around its pivot
     * (pivot), and whether it is changed at all. All unchanged while the module is off.
     */
    public final int[] dx = new int[PARTS.length], dy = new int[PARTS.length];
    public final float[] scale = {1, 1, 1, 1, 1, 1};
    public final boolean[] moved = new boolean[PARTS.length];

    private final SliderOption[] xs = new SliderOption[PARTS.length], ys = new SliderOption[PARTS.length], scales = new SliderOption[PARTS.length];

    public void refresh() {
        boolean on = isEnabled();
        for (int part = 0; part < PARTS.length; part++) {
            dx[part] = on ? xs[part].getIntValue() : 0;
            dy[part] = on && ys[part] != null ? ys[part].getIntValue() : 0;
            scale[part] = on ? scales[part].getIntValue() / 100f : 1;
            moved[part] = dx[part] != 0 || dy[part] != 0 || scale[part] != 1;
        }
    }

    /** The point a part scales around on a w x h GUI: the hotbar's bottom middle, each bar's outer bottom corner. */
    public static float pivotX(int part, int w) {
        return part == HOTBAR ? w / 2f : part == HEALTH || part == ARMOR ? w / 2f - 91 : w / 2f + 91;
    }

    public static float pivotY(int part, int h) {
        return part == HOTBAR ? h : part == ARMOR || part == AIR ? h - 40 : h - 30;
    }

    /** The registered module, or null when none is (a version that never registered it). */
    public static RaisedModule get() {
        RaisedModule instance = cachedInstance;
        if (instance == null) {
            if (ModuleManager.getInstance().getModule(NAME) instanceof RaisedModule raised) {
                cachedInstance = instance = raised;
            }
        }
        return instance;
    }
}
