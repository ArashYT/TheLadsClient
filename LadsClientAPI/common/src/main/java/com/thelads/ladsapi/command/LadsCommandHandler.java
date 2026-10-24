package com.thelads.ladsapi.command;

import com.thelads.ladsapi.LadsCore;
import com.thelads.ladsapi.api.LadsClientAPI;
import com.thelads.ladsapi.api.LadsPlayer;
import com.thelads.ladsapi.platform.LadsPlayerHandle;
import com.thelads.ladsapi.session.LadsSession;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles /ladsapi subcommands: reload, status, inspect, set, clear.
 */
public final class LadsCommandHandler {
    private LadsCommandHandler() {}

    public static List<String> handleCommand(LadsCore core, String[] args, boolean admin) {
        if (!admin) {
            return List.of("§cYou do not have permission to run /ladsapi.");
        }

        if (args == null || args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            List<String> lines = new ArrayList<>();
            lines.add("§6--- LadsClientAPI Commands ---");
            lines.add("§e/ladsapi reload §7- Reload configuration and rules files");
            lines.add("§e/ladsapi status §7- Show plugin status and connected Lads players");
            lines.add("§e/ladsapi inspect <player> §7- Inspect player's client and active policy");
            lines.add("§e/ladsapi set <player> <Module> <state> §7- Set module state (allow|block|on)");
            lines.add("§e/ladsapi set <player> <Module.Option> <rule> §7- Set option rule (max=10, min=0, value=...)");
            lines.add("§e/ladsapi clear <player> §7- Clear all API overrides for a player");
            return lines;
        }

        String sub = args[0].toLowerCase();
        return switch (sub) {
            case "reload" -> handleReload(core);
            case "status" -> handleStatus(core);
            case "inspect" -> handleInspect(core, args);
            case "set" -> handleSet(core, args);
            case "clear" -> handleClear(core, args);
            default -> List.of("§cUnknown subcommand: " + args[0] + ". Use /ladsapi help for commands.");
        };
    }

    private static List<String> handleReload(LadsCore core) {
        core.reload();
        return List.of("§a[LadsClientAPI] Configuration and rules reloaded. Policies resent.");
    }

    private static List<String> handleStatus(LadsCore core) {
        List<String> lines = new ArrayList<>();
        lines.add("§6--- LadsClientAPI Status ---");
        lines.add("§7Platform: §f" + core.platform().id() + (core.platform().isProxy() ? " (proxy)" : ""));
        int totalOnline = core.platform().online() != null ? core.platform().online().size() : 0;
        List<UUID> ladsUuids = core.sessionManager().getAllLadsPlayerUuids();
        lines.add("§7Online players: §f" + totalOnline + " §7| Lads Client: §a" + ladsUuids.size());
        if (!ladsUuids.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (UUID u : ladsUuids) {
                LadsSession s = core.sessionManager().getSessions().get(u);
                if (s != null) names.add(s.getHandle().name());
            }
            lines.add("§7Lads players: §f" + String.join(", ", names));
        }
        return lines;
    }

    private static List<String> handleInspect(LadsCore core, String[] args) {
        if (args.length < 2) {
            return List.of("§cUsage: /ladsapi inspect <player>");
        }
        LadsPlayerHandle handle = findOnlinePlayer(core, args[1]);
        if (handle == null) {
            return List.of("§cPlayer not found: " + args[1]);
        }
        LadsSession s = core.sessionManager().getSessions().get(handle.uuid());
        if (s == null || !s.isLads()) {
            return List.of("§7Player §f" + handle.name() + " §7is §cnot §7running Lads Client.");
        }

        List<String> lines = new ArrayList<>();
        lines.add("§6--- LadsClientAPI Inspect: " + handle.name() + " ---");
        lines.add("§7UUID: §f" + handle.uuid());
        lines.add("§7Client: §fLads v" + s.getLadsVersion() + " §7(MC: §f" + s.getMcVersion() + "§7)");
        lines.add("§7World: §f" + handle.world());
        lines.add("§7Active policy rev: §f" + s.getRev());
        if (!s.getApiOverrides().isEmpty()) {
            lines.add("§7API overrides: §e" + s.getApiOverrides().keySet());
        }
        return lines;
    }

    private static List<String> handleSet(LadsCore core, String[] args) {
        if (args.length < 4) {
            return List.of("§cUsage: /ladsapi set <player> <Module> <state> OR /ladsapi set <player> <Module.Option> <rule>");
        }
        LadsPlayerHandle handle = findOnlinePlayer(core, args[1]);
        if (handle == null) {
            return List.of("§cPlayer not found: " + args[1]);
        }
        Optional<LadsPlayer> optLp = LadsClientAPI.get().player(handle.uuid());
        if (optLp.isEmpty()) {
            return List.of("§cPlayer " + handle.name() + " is not running Lads Client.");
        }
        LadsPlayer lp = optLp.get();

        String target = args[2];
        String ruleVal = args[3];

        if (target.contains(".")) {
            // Module.Option
            int dot = target.indexOf('.');
            String module = target.substring(0, dot);
            String option = target.substring(dot + 1);

            String lower = ruleVal.toLowerCase();
            if (lower.startsWith("max=")) {
                try {
                    double max = Double.parseDouble(ruleVal.substring(4));
                    lp.limit(module, option, Double.NaN, max);
                } catch (NumberFormatException e) {
                    return List.of("§cInvalid number: " + ruleVal.substring(4));
                }
            } else if (lower.startsWith("min=")) {
                try {
                    double min = Double.parseDouble(ruleVal.substring(4));
                    lp.limit(module, option, min, Double.NaN);
                } catch (NumberFormatException e) {
                    return List.of("§cInvalid number: " + ruleVal.substring(4));
                }
            } else if (lower.startsWith("value=")) {
                String valStr = ruleVal.substring(6);
                lp.force(module, option, parseScalarValue(valStr));
            } else {
                lp.force(module, option, parseScalarValue(ruleVal));
            }
            return List.of("§aSet " + target + " to " + ruleVal + " for " + handle.name());
        } else {
            // Module state
            String state = ruleVal.toLowerCase();
            switch (state) {
                case "allow" -> lp.allow(target);
                case "block" -> lp.block(target);
                case "on" -> lp.forceOn(target);
                default -> {
                    return List.of("§cInvalid state: " + ruleVal + " (expected: allow | block | on)");
                }
            }
            return List.of("§aSet module " + target + " state to " + state + " for " + handle.name());
        }
    }

    private static List<String> handleClear(LadsCore core, String[] args) {
        if (args.length < 2) {
            return List.of("§cUsage: /ladsapi clear <player>");
        }
        LadsPlayerHandle handle = findOnlinePlayer(core, args[1]);
        if (handle == null) {
            return List.of("§cPlayer not found: " + args[1]);
        }
        Optional<LadsPlayer> optLp = LadsClientAPI.get().player(handle.uuid());
        if (optLp.isEmpty()) {
            return List.of("§cPlayer " + handle.name() + " is not running Lads Client.");
        }
        optLp.get().clear(null);
        return List.of("§aCleared all API overrides for " + handle.name());
    }

    private static LadsPlayerHandle findOnlinePlayer(LadsCore core, String nameOrUuid) {
        if (core.platform().online() == null) return null;
        for (LadsPlayerHandle h : core.platform().online()) {
            if (h.name().equalsIgnoreCase(nameOrUuid) || h.uuid().toString().equalsIgnoreCase(nameOrUuid)) {
                return h;
            }
        }
        return null;
    }

    private static Object parseScalarValue(String val) {
        if ("true".equalsIgnoreCase(val)) return true;
        if ("false".equalsIgnoreCase(val)) return false;
        try {
            if (val.contains(".")) {
                return Double.parseDouble(val);
            } else {
                return Long.parseLong(val);
            }
        } catch (NumberFormatException ignored) {
            return val;
        }
    }
}
