package com.thelads.ladsapi.velocity;

import com.thelads.ladsapi.LadsCore;
import com.thelads.ladsapi.schema.LadsSchema;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Handles /ladsapi command on Velocity.
 */
public final class VelocityCommandHandler implements SimpleCommand {
    private final LadsCore core;
    private final ProxyServer server;

    public VelocityCommandHandler(LadsCore core, ProxyServer server) {
        this.core = core;
        this.server = server;
    }

    @Override
    public void execute(Invocation invocation) {
        boolean admin = invocation.source().hasPermission("ladsapi.admin");
        List<String> output = core.command(invocation.arguments(), admin);
        for (String line : output) {
            invocation.source().sendMessage(LegacyComponentSerializer.legacySection().deserialize(line));
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (!invocation.source().hasPermission("ladsapi.admin")) {
            return Collections.emptyList();
        }

        String[] args = invocation.arguments();
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0];
            return filterMatching(List.of("reload", "status", "inspect", "set", "clear"), prefix);
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if ("inspect".equals(sub) || "set".equals(sub) || "clear".equals(sub)) {
                List<String> names = new ArrayList<>();
                for (Player p : server.getAllPlayers()) {
                    names.add(p.getUsername());
                }
                return filterMatching(names, args[1]);
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
