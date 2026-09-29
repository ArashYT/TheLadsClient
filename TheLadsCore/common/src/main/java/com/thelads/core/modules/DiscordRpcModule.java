package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.TextOption;

public class DiscordRpcModule extends Module {
    public final TextOption applicationId = addOption(new TextOption("Application ID", ""));
    public final BoolOption shareActivity = addOption(new BoolOption("Share activity", false));
    public final BoolOption shareServerAddress = addOption(new BoolOption("Share server address", false));
    public final BoolOption shareWorldName = addOption(new BoolOption("Share world name", false));
    public final BoolOption showDimension = addOption(new BoolOption("Show dimension", false));
    public final BoolOption showElapsed = addOption(new BoolOption("Show elapsed time", true));
    public final DropdownOption detailLevel = addOption(new DropdownOption("Detail Level", 1, "Full", "Simple", "Minimal"));
    private volatile String status = "Coming soon - Discord presence will be available in a future update.";
    public DiscordRpcModule() {
        super("DiscordRPC", "Coming soon");
    }
    public void setStatus(String status) { /* Deferred until the Lads application is configured. */ }
    @Override public String getDescription() { return status; }
}
