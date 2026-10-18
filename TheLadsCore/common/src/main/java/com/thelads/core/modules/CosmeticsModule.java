package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;

/** Emotes (later capes, hats...) seen by every Lads player on your server, through our own server, never the game's connection. */
public class CosmeticsModule extends Module {
    public final BoolOption showOthers = addOption(new BoolOption("Show other players' emotes", true));
    public final BoolOption shareMine = addOption(new BoolOption("Share my emotes", true));

    public CosmeticsModule() {
        super("Cosmetics", "Emotes other Lads Client players on your server can see. While on (in multiplayer only), your Minecraft name, "
            + "UUID and a hash of the server address go to the Lads cosmetics server; nothing is stored on disk.");
        setEnabled(true);
    }
}
