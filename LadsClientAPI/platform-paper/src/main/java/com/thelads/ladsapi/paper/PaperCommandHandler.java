package com.thelads.ladsapi.paper;

import com.thelads.ladsapi.LadsCore;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * Executes /ladsapi command on Paper/Spigot.
 */
public final class PaperCommandHandler implements CommandExecutor {
    private final LadsCore core;

    public PaperCommandHandler(LadsCore core) {
        this.core = core;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean admin = sender.hasPermission("ladsapi.admin");
        List<String> output = core.command(args, admin);
        for (String line : output) {
            sender.sendMessage(line);
        }
        return true;
    }
}
