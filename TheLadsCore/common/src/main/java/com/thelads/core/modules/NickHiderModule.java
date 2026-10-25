package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.TextOption;

/**
 * Nick Hider: replaces player username and skin client-side to protect streamer identity (parity with Lunar / Feather / Badlion).
 */
public class NickHiderModule extends Module {
    public static final String NAME = "Nick Hider";

    public final BoolOption hideOwnName;
    public final TextOption customNick;
    public final BoolOption hideOwnSkin;
    public final BoolOption hideOthersSkins;
    public final BoolOption hideOthersNames;
    public final TextOption othersNick;

    public NickHiderModule() {
        super(NAME, "Replaces client-side player username and skin to protect streamer identity.");
        hideOwnName = addOption(new BoolOption("Hide own name", true));
        customNick = addOption(new TextOption("Custom nickname", "You"));
        hideOwnSkin = addOption(new BoolOption("Hide own skin", true));
        hideOthersSkins = addOption(new BoolOption("Hide others' skins", false));
        hideOthersNames = addOption(new BoolOption("Hide others' names", false));
        othersNick = addOption(new TextOption("Others' nickname", "Player"));
        setEnabled(false);
    }
}
