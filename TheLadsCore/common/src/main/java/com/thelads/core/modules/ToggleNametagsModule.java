package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;
import com.thelads.core.config.TextOption;

/** On by default: every option but Text Shadow matches vanilla. Renames are display-only (client/Nicknames). */
public class ToggleNametagsModule extends Module {
    public final BoolOption showOwnNametag;
    public final BoolOption renderBackground;
    public final BoolOption textShadow;
    public final TextOption displayName;
    public final TextOption nicknames;
    public final SliderOption scale;
    public final SliderOption backgroundOpacity;
    public final BoolOption distanceFade;
    public final BoolOption showHealth;
    public final BoolOption showPing;
    public final BoolOption showInF1;

    public ToggleNametagsModule() {
        super("Nametags", "Nametag shadow, background and own tag. Rename players for you only: Nicknames as Name=Nick, Name2=Nick2.");
        showOwnNametag = addOption(new BoolOption("Show Own Nametag in Third Person", false));
        renderBackground = addOption(new BoolOption("Render Background", false));
        textShadow = addOption(new BoolOption("Text Shadow", true));
        displayName = addOption(new TextOption("Your Display Name", ""));
        nicknames = addOption(new TextOption("Nicknames", ""));
        scale = addOption(new SliderOption("Nametag Scale", 1.0, 0.5, 2.0, 0.05));
        backgroundOpacity = addOption(new SliderOption("Background Opacity", 25, 0, 100, 5).percent());
        distanceFade = addOption(new BoolOption("Distance Fade", false));
        showHealth = addOption(new BoolOption("Show Health", false));
        showPing = addOption(new BoolOption("Show Ping Badge", false));
        showInF1 = addOption(new BoolOption("Show Nametags in F1", false));
        setEnabled(true);
    }
}
