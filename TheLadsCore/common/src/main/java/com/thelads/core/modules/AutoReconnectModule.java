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
        super("AutoReconnect", "Retry interrupted server, Realm or local-world sessions. Configure delays, reasons and optional actions; Escape cancels a pending retry.");
    }
}
