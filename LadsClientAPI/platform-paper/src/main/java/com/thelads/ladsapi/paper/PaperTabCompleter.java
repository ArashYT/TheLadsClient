package com.thelads.ladsapi.paper;

import com.thelads.ladsapi.LadsCore;
import com.thelads.ladsapi.schema.LadsSchema;
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

        if (args.length == 1) {
            List<String> subs = List.of("reload", "status", "inspect", "set", "clear");
            return filterMatching(subs, args[0]);
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if ("inspect".equals(sub) || "set".equals(sub) || "clear".equals(sub)) {
                List<String> players = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    players.add(p.getName());
                }
                return filterMatching(players, args[1]);
            }
        }

        if (args.length == 3 && "set".equalsIgnoreCase(args[0])) {
            List<String> targets = new ArrayList<>();
            if (core.schema() != null && core.schema().getModules() != null) {
                for (LadsSchema.SchemaModule mod : core.schema().getModules()) {
                    targets.add(mod.getName());
                    if (mod.getOptions() != null) {
                        for (LadsSchema.SchemaOption opt : mod.getOptions()) {
                            targets.add(mod.getName() + "." + opt.getName());
                        }
                    }
                }
            }
            return filterMatching(targets, args[2]);
        }

        if (args.length == 4 && "set".equalsIgnoreCase(args[0])) {
            String target = args[2];
            if (target.contains(".")) {
                return filterMatching(List.of("max=", "min=", "value="), args[3]);
            } else {
                return filterMatching(List.of("allow", "block", "on"), args[3]);
            }
        }

        return Collections.emptyList();
    }

    private static List<String> filterMatching(List<String> options, String prefix) {
        if (prefix == null || prefix.isEmpty()) return options;
        String lower = prefix.toLowerCase();
        List<String> res = new ArrayList<>();
        for (String opt : options) {
            if (opt.toLowerCase().startsWith(lower)) {
                res.add(opt);
            }
        }
        return res;
    }
}
