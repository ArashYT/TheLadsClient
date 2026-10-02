package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.TextOption;

/** On by default: every option but Text Shadow matches vanilla. Renames are display-only (client/Nicknames). */
public class ToggleNametagsModule extends Module {
    public final TextOption displayName;
    public final TextOption nicknames;

    public ToggleNametagsModule() {
        super("Nametags", "Nametag shadow, background and own tag. Rename players for you only: Nicknames as Name=Nick, Name2=Nick2.");
        addOption(new BoolOption("Show Own Nametag in Third Person", false));
        addOption(new BoolOption("Render Background", true));
        addOption(new BoolOption("Text Shadow", true));
        displayName = addOption(new TextOption("Your Display Name", ""));
        nicknames = addOption(new TextOption("Nicknames", ""));
        setEnabled(true);
    }
}
