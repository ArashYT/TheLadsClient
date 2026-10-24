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

    private final SliderOption optHotbar;
    private final SliderOption optDistance;
    private final SliderOption optChat;
    private static volatile RaisedModule cachedInstance = null;

    public RaisedModule() {
        super(NAME, "Lifts the hotbar, the bars on it and the action bar off the bottom of the screen, and above the chat box while "
            + "chat is open (Distance). Chat can be raised too.");
        addOption(optHotbar = new SliderOption("Hotbar", 2, 0, 50, 1));
        // Its 1.6.0 name and value, so saved settings carry over: the extra lift while chat is open.
        addOption(optDistance = new SliderOption("Distance", 14, 0, 50, 1));
        addOption(optChat = new SliderOption("Chat", 0, 0, 100, 1));
        addOption(new SliderOption("Hotbar Scale", 100, 50, 150, 5));
        addOption(new SliderOption("Health X Offset", 0, -200, 200, 1));
        addOption(new SliderOption("Health Y Offset", 0, -200, 200, 1));
        addOption(new SliderOption("Health Scale", 100, 50, 150, 5));
        addOption(new SliderOption("Hunger X Offset", 0, -200, 200, 1));
        addOption(new SliderOption("Hunger Y Offset", 0, -200, 200, 1));
        addOption(new SliderOption("Hunger Scale", 100, 50, 150, 5));
        addOption(new SliderOption("Armor X Offset", 0, -200, 200, 1));
        addOption(new SliderOption("Armor Y Offset", 0, -200, 200, 1));
        addOption(new SliderOption("Air & Mount X Offset", 0, -200, 200, 1));
        addOption(new SliderOption("Air & Mount Y Offset", 0, -200, 200, 1));
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

    public float hotbarScale() { return isEnabled() ? Math.max(0.5f, pixels("Hotbar Scale") / 100.0f) : 1.0f; }
    public int healthOffsetX() { return isEnabled() ? pixels("Health X Offset") : 0; }
    public int healthOffsetY() { return isEnabled() ? pixels("Health Y Offset") : 0; }
    public float healthScale() { return isEnabled() ? Math.max(0.5f, pixels("Health Scale") / 100.0f) : 1.0f; }
    public int hungerOffsetX() { return isEnabled() ? pixels("Hunger X Offset") : 0; }
    public int hungerOffsetY() { return isEnabled() ? pixels("Hunger Y Offset") : 0; }
    public float hungerScale() { return isEnabled() ? Math.max(0.5f, pixels("Hunger Scale") / 100.0f) : 1.0f; }
    public int armorOffsetX() { return isEnabled() ? pixels("Armor X Offset") : 0; }
    public int armorOffsetY() { return isEnabled() ? pixels("Armor Y Offset") : 0; }
    public int airMountOffsetX() { return isEnabled() ? pixels("Air & Mount X Offset") : 0; }
    public int airMountOffsetY() { return isEnabled() ? pixels("Air & Mount Y Offset") : 0; }

    private int pixels(String option) {
        return getOption(option) instanceof SliderOption slider ? slider.getIntValue() : 0;
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
