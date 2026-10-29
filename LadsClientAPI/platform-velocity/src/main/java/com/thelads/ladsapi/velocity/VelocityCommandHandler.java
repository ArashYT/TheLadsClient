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
        List<String> names = new ArrayList<>();
        for (Player p : server.getAllPlayers()) {
            names.add(p.getUsername());
        }
        return com.thelads.ladsapi.command.LadsCommandHandler.suggest(core, invocation.arguments(), names);
    }
}
