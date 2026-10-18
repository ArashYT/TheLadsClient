package com.thelads.core.modules;

import com.thelads.core.client.cosmetics.anim.EmoteLibrary;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.StringOption;
import java.util.List;

/**
 * Emotes (later capes, hats...) seen by every Lads player on your server, through our own server, never the game's connection.
 * Always on and hidden from the Mods list since 1.9.12: its settings live in the Cosmetics screen (title screen and pause menu).
 */
public class CosmeticsModule extends Module {
    public static final String NAME = "Cosmetics";
    public final BoolOption showOthers = addOption(new BoolOption("Show other players' emotes", true));
    public final BoolOption shareMine = addOption(new BoolOption("Share my emotes", true));
    /** The wheel's slots, clockwise from the top, as emote ids joined by commas ("" in a slot = empty); "" = the default wheel. */
    public final StringOption wheel = addOption(new StringOption("Wheel", ""));

    public CosmeticsModule() {
        super(NAME, "Emotes other Lads Client players on your server can see. In multiplayer your Minecraft name, UUID and a hash of the "
            + "server address go to the Lads cosmetics server; nothing is stored on disk. Hold B (Controls: Emotes) for the emote wheel.");
        setEnabled(true);
    }

    /** The registered module (a default one where the manager has none). */
    public static CosmeticsModule get() {
        return ModuleManager.getInstance().getModule(NAME) instanceof CosmeticsModule m ? m : new CosmeticsModule();
    }

    @Override public boolean isEnabled() { return true; }
    @Override public void setEnabled(boolean enabled) { super.setEnabled(true); }

    /** The wheel's slots (null = empty); unknown ids (an emote that is gone) read as empty. */
    public String[] slots() {
        String saved = wheel.get();
        if (saved == null || saved.isBlank()) return defaultSlots();
        String[] out = new String[EmoteLibrary.SLOTS], parts = saved.split(",", -1);
        for (int i = 0; i < out.length && i < parts.length; i++) out[i] = EmoteLibrary.get(parts[i].trim()) == null ? null : parts[i].trim();
        return out;
    }

    /** Puts {@code id} (null = clear) in slot {@code index}; an emote already elsewhere on the wheel moves there. */
    public void setSlot(int index, String id) {
        String[] s = slots();
        for (int i = 0; i < s.length; i++) if (id != null && id.equals(s[i])) s[i] = null;
        s[index] = id;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length; i++) b.append(i == 0 ? "" : ",").append(s[i] == null ? "" : s[i]);
        wheel.set(b.toString());
        touch();
    }

    /** The first SLOTS emotes of the library, in order. */
    public static String[] defaultSlots() {
        String[] out = new String[EmoteLibrary.SLOTS];
        List<String> ids = EmoteLibrary.ids();
        for (int i = 0; i < out.length && i < ids.size(); i++) out[i] = ids.get(i);
        return out;
    }
}
