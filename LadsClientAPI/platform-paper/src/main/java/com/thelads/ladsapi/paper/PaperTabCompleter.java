package com.thelads.ladsapi.paper;

import com.thelads.ladsapi.LadsCore;
import com.thelads.ladsapi.command.LadsCommandHandler;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tab completer for /ladsapi on Paper/Spigot.
 */
public final class PaperTabCompleter implements TabCompleter {
    private final LadsCore core;

    public PaperTabCompleter(LadsCore core) {
        this.core = core;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("ladsapi.admin")) {
            return Collections.emptyList();
        }
        List<String> players = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            players.add(p.getName());
        }
        return LadsCommandHandler.suggest(core, args, players);
    }
}
