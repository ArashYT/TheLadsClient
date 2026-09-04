package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;

public class DiscordRpcModule extends Module {
    public DiscordRpcModule() {
        super("DiscordRPC", "Show rich presence on Discord.");
        addOption(new BoolOption("Show Server IP", true));
        addOption(new DropdownOption("Detail Level", 0, "Full", "Simple", "Minimal"));
    }
}
