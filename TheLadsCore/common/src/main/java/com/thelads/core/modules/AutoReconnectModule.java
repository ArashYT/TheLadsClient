package com.thelads.core.modules;

import com.thelads.core.config.ActionOption;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;

public final class AutoReconnectModule extends Module {
    public final BoolOption initial = addOption(new BoolOption("Retry Initial Failures", false));
    public final BoolOption infinite = addOption(new BoolOption("Repeat Last Delay", false));
    public final DropdownOption reasonMode = addOption(new DropdownOption("Reason Filter", 0, "Except Matches", "Only Matches"));
    public final ActionOption retryEditor = addOption(new ActionOption("Delays and Disconnect Filters", "Edit"));
    public final BoolOption actionsEnabled = addOption(new BoolOption("Enable Reconnect Actions", false));
    public final BoolOption regexIds = addOption(new BoolOption("Match Action IDs as Regex", false));
    public final BoolOption signedCommands = addOption(new BoolOption("Sign Configured Commands", false));
    public final ActionOption actionsEditor = addOption(new ActionOption("Actions After Reconnecting", "Edit"));
    public AutoReconnectModule() {
        super("AutoReconnect", "After an unexpected disconnect from a server or local world, the disconnect screen counts down and reconnects. "
            + "Kicks and bans don't retry. Configure the delays, reason filters and messages to send after reconnecting; Cancel or Escape stops a countdown.");
    }
}
