package com.thelads.core.v1_20_1.hud.chat;

import com.thelads.core.v1_20_1.hud.overlay.Nametags1201;
import net.minecraft.network.chat.Component;

/**
 * Nametags renames (Your Display Name, Nicknames) for arriving chat and the tab list, display-only. The Nametags group can call
 * {@link #rename} on an entity's name tag text too.
 */
public final class NativeNicknames1201 {
    private NativeNicknames1201() {}

    /** The text with renamed players, each piece keeping its style; the same instance when nothing is renamed. */
    public static Component rename(Component text) {
        if (text == null) return null;
        return Nametags1201.rename(text);
    }
}
