package com.thelads.ladsapi.command;

import com.thelads.ladsapi.schema.LadsSchema;
import java.util.Collections;
import java.util.LinkedHashSet;
import com.thelads.ladsapi.LadsCore;
import com.thelads.ladsapi.api.LadsClientAPI;
import com.thelads.ladsapi.api.LadsPlayer;
import com.thelads.ladsapi.platform.LadsPlayerHandle;
import com.thelads.ladsapi.rules.ModuleRule;
import com.thelads.ladsapi.rules.RuleOption;
import com.thelads.ladsapi.session.KeptSettings;
import com.thelads.ladsapi.session.LadsSession;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
            lines.add("§e/ladsapi set <player> <Module> <state> [--keep] §7- Set module state (allow|block|on)");
            lines.add("§e/ladsapi set <player> <Module.Option> <rule> [--keep] §7- Set option rule (max=10, min=0, value=...)");
            lines.add("§7  A set lasts until the player logs out; §e--keep§7 also saves it in players.yml for every later login.");
            lines.add("§e/ladsapi clear <player> [Module] §7- Clear temporary and kept overrides (all, or one module); kept ones work offline");
            lines.add("§e/ladsapi scope <player> <name> [seconds|off|--keep] §7- Switch a rules.yml scope on (some seconds, or kept for every login) or off");
            lines.add("§e/ladsapi announce <text> §7- Show a toast on every connected Lads client");
            return lines;
        }

        String sub = args[0].toLowerCase();
        return switch (sub) {
            case "reload" -> handleReload(core);
            case "status" -> handleStatus(core);
            case "inspect" -> handleInspect(core, args);
            case "set" -> handleSet(core, args);
            case "clear" -> handleClear(core, args);
            case "scope" -> handleScope(core, args);
            case "announce" -> handleAnnounce(core, args);
            default -> List.of("§cUnknown subcommand: " + args[0] + ". Use /ladsapi help for commands.");
        };
    }

    private static List<String> handleAnnounce(LadsCore core, String[] args) {
        if (args.length < 2) return List.of("§cUsage: /ladsapi announce <text>");
        String text = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        String title = core.sessionManager().getConfig().getServerName();
        int sent = 0;
        for (var session : core.sessionManager().getSessions().values()) {
            if (session.isLads()) {
                session.sendToast(title, text);
                sent++;
            }
        }
        return List.of("§a[LadsClientAPI] Announced to " + sent + " Lads player(s).");
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
                if (s != null) names.add(s.getHandle().name() + (s.isVerified() ? " §a(verified)§f" : " §e(unverified)§f"));
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
        lines.add("§7Verified by the Lads relay: " + (s.isVerified() ? "§ayes" : "§eno"));
        lines.add("§7World: §f" + handle.world());
        lines.add("§7Active policy rev: §f" + s.getRev());
        if (!s.getApiOverrides().isEmpty()) {
            lines.add("§7Temporary overrides (until logout): §e" + s.getApiOverrides().keySet());
        }
        List<String> keptScopes = core.sessionManager().kept().scopes(handle.uuid());
        List<String> nowScopes = new ArrayList<>(s.scopeNames());
        nowScopes.removeAll(keptScopes);
        if (!nowScopes.isEmpty()) lines.add("§7Temporary scopes: §e" + nowScopes);
        Map<String, ModuleRule> keptRules = core.sessionManager().kept().rules(handle.uuid());
        if (!keptRules.isEmpty()) lines.add("§7Kept overrides (players.yml): §e" + keptRules.keySet());
        if (!keptScopes.isEmpty()) lines.add("§7Kept scopes (players.yml): §e" + keptScopes);
        return lines;
    }

    record ResolvedTarget(String module, String option, boolean inSchema) {
        ResolvedTarget(String module, String option) {
            this(module, option, true);
        }
    }

    static ResolvedTarget resolveTarget(LadsSchema schema, String target) {
        if (target == null || target.isBlank()) return null;

        if (schema != null && schema.getModules() != null) {
            // 1. Check if target matches a module name exactly (case-insensitive)
            for (LadsSchema.SchemaModule mod : schema.getModules()) {
                if (mod.getName().equalsIgnoreCase(target)) {
                    return new ResolvedTarget(mod.getName(), null, true);
                }
            }

            // 2. Check if target is Module.Option (longest module prefix wins)
            LadsSchema.SchemaModule bestMod = null;
            String matchedOptName = null;
            for (LadsSchema.SchemaModule mod : schema.getModules()) {
                String modPrefix = mod.getName().toLowerCase() + ".";
                if (target.toLowerCase().startsWith(modPrefix)) {
                    if (bestMod == null || mod.getName().length() > bestMod.getName().length()) {
                        String optPart = target.substring(modPrefix.length());
                        if (mod.getOptions() != null) {
                            for (LadsSchema.SchemaOption opt : mod.getOptions()) {
                                if (opt.getName().equalsIgnoreCase(optPart)) {
                                    bestMod = mod;
                                    matchedOptName = opt.getName();
                                    break;
                                }
                            }
                        }
                    }
                }
            }
            if (bestMod != null && matchedOptName != null) {
                return new ResolvedTarget(bestMod.getName(), matchedOptName, true);
            }

            // 3. Check if target starts with a known module name followed by '.' even if option is not in schema
            LadsSchema.SchemaModule prefixMod = null;
            for (LadsSchema.SchemaModule mod : schema.getModules()) {
                String modPrefix = mod.getName().toLowerCase() + ".";
                if (target.toLowerCase().startsWith(modPrefix)) {
                    if (prefixMod == null || mod.getName().length() > prefixMod.getName().length()) {
                        prefixMod = mod;
                    }
                }
            }
            if (prefixMod != null) {
                String optPart = target.substring(prefixMod.getName().length() + 1);
                return new ResolvedTarget(prefixMod.getName(), optPart, false);
            }
        }

        // Fallback for names not found in schema
        int dot = target.lastIndexOf('.');
        if (dot > 0 && dot < target.length() - 1) {
            return new ResolvedTarget(target.substring(0, dot), target.substring(dot + 1), false);
        }
        return new ResolvedTarget(target, null, false);
    }

    /** Splits "--keep" out of the words after the player (it may stand anywhere there). */
    private static boolean takeKeep(List<String> words) {
        boolean keep = false;
        for (int i = words.size() - 1; i >= 2; i--) {
            if ("--keep".equalsIgnoreCase(words.get(i))) {
                words.remove(i);
                keep = true;
            }
        }
        return keep;
    }

    private static List<String> handleSet(LadsCore core, String[] rawArgs) {
        List<String> words = new ArrayList<>(List.of(rawArgs));
        boolean keep = takeKeep(words);
        String[] args = words.toArray(new String[0]);
        if (args.length < 4) {
            return List.of("§cUsage: /ladsapi set <player> <Module> <state> [--keep] OR /ladsapi set <player> <Module.Option> <rule> [--keep]");
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

        String target = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length - 1));
        String ruleVal = args[args.length - 1];

        ResolvedTarget resolved = resolveTarget(core.schema(), target);
        if (resolved == null) {
            return List.of("§cUnknown module or option: " + target);
        }

        List<String> output = new ArrayList<>();
        if (!resolved.inSchema()) {
            output.add("§eWarning: '" + target + "' is not in schema.");
        }

        // The rule as a one-module layer: applied to this session (temporary) or saved (--keep).
        ModuleRule rule = new ModuleRule();
        String what;
        if (resolved.option != null) {
            RuleOption opt = new RuleOption();
            String lower = ruleVal.toLowerCase();
            try {
                if (lower.startsWith("max=")) {
                    opt.setMax(Double.parseDouble(ruleVal.substring(4)));
                } else if (lower.startsWith("min=")) {
                    opt.setMin(Double.parseDouble(ruleVal.substring(4)));
                } else {
                    opt.setValue(parseScalarValue(lower.startsWith("value=") ? ruleVal.substring(6) : ruleVal));
                    opt.setLock(true);
                }
            } catch (NumberFormatException e) {
                return List.of("§cInvalid number: " + ruleVal.substring(4));
            }
            rule.setOption(resolved.option, opt);
            what = resolved.module + "." + resolved.option + " to " + ruleVal;
        } else {
            String state = ruleVal.toLowerCase();
            if (!state.equals("allow") && !state.equals("block") && !state.equals("on")) {
                return List.of("§cInvalid state: " + ruleVal + " (expected: allow | block | on)");
            }
            rule.setState(state);
            what = "module " + resolved.module + " state to " + state;
        }

        if (keep) {
            try {
                if (!core.sessionManager().kept().put(handle.uuid(), handle.name(), resolved.module, rule)) {
                    output.add("§cCould not write players.yml (see the server log): the rule only lasts until the next restart.");
                }
            } catch (IllegalArgumentException e) {
                return List.of("§c" + e.getMessage());
            }
            lp.resend();
            output.add("§aSet " + what + " for " + handle.name() + " §7(kept: every login)");
            return output;
        }
        applyTemporary(lp, resolved.module, rule);
        output.add("§aSet " + what + " for " + handle.name() + " §7(until logout)");
        return output;
    }

    private static void applyTemporary(LadsPlayer lp, String module, ModuleRule rule) {
        if (rule.getState() != null) {
            switch (rule.getState()) {
                case "allow" -> lp.allow(module);
                case "block" -> lp.block(module);
                default -> lp.forceOn(module);
            }
        }
        for (Map.Entry<String, RuleOption> e : rule.getOptions().entrySet()) {
            RuleOption o = e.getValue();
            if (o.getValue() != null) lp.force(module, e.getKey(), o.getValue());
            else lp.limit(module, e.getKey(), o.getMin() == null ? Double.NaN : o.getMin(), o.getMax() == null ? Double.NaN : o.getMax());
        }
    }

    /** /ladsapi clear <player> [Module]: temporary and kept overrides; an offline player's kept ones too (name or UUID). */
    private static List<String> handleClear(LadsCore core, String[] args) {
        if (args.length < 2 || args.length > 3) {
            return List.of("§cUsage: /ladsapi clear <player> [Module]");
        }
        String module = null;
        if (args.length == 3) {
            ResolvedTarget resolved = resolveTarget(core.schema(), args[2]);
            if (resolved == null || resolved.option != null) {
                return List.of("§cclear takes a module name, not Module.Option: " + args[2]);
            }
            module = resolved.module;
        }
        KeptSettings kept = core.sessionManager().kept();
        LadsPlayerHandle handle = findOnlinePlayer(core, args[1]);
        UUID uuid = handle != null ? handle.uuid() : kept.find(args[1]);
        if (uuid == null) {
            return List.of("§cPlayer not found: " + args[1]);
        }
        String who = handle != null ? handle.name() : (kept.name(uuid).isEmpty() ? uuid.toString() : kept.name(uuid));
        List<String> scopesGone = module == null ? kept.scopes(uuid) : List.of();
        boolean keptGone = kept.clear(uuid, module);
        boolean tempGone = false;
        LadsSession session = core.sessionManager().getSessions().get(uuid);
        if (session != null) {
            for (String scope : scopesGone) session.endScope(scope);
            tempGone = module == null ? !session.getApiOverrides().isEmpty() : session.getApiOverrides().containsKey(module);
            session.clearApiOverrides(module);
            if (session.isLads()) session.sendPolicy();
        }
        if (!keptGone && !tempGone) {
            return List.of("§e" + who + " had no " + (module == null ? "" : module + " ") + "overrides to clear.");
        }
        return List.of("§aCleared " + (module == null ? "all" : module) + " overrides for " + who
                + " §7(" + (tempGone ? "temporary" : "") + (tempGone && keptGone ? " + " : "") + (keptGone ? "kept" : "") + ")");
    }

    /** /ladsapi scope <player> <name> [seconds|off|--keep]: switches a rules.yml scope on (for good, for some seconds, or kept) or off. */
    private static List<String> handleScope(LadsCore core, String[] rawArgs) {
        List<String> words = new ArrayList<>(List.of(rawArgs));
        boolean keep = takeKeep(words);
        String[] args = words.toArray(new String[0]);
        if (args.length < 3) {
            return List.of("§cUsage: /ladsapi scope <player> <name> [seconds|off|--keep]");
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
        KeptSettings kept = core.sessionManager().kept();
        String name = args[2].toLowerCase();
        if (args.length >= 4 && "off".equalsIgnoreCase(args[3])) {
            boolean wasKept = kept.removeScope(handle.uuid(), name);
            boolean wasOn = lp.scopes().contains(name);
            if (!wasOn && !wasKept) return List.of("§e" + handle.name() + " is not in scope " + name);
            if (wasOn) lp.scope(name).end();
            return List.of("§aScope " + name + " ended for " + handle.name() + (wasKept ? " §7(no longer kept)" : ""));
        }
        long seconds = 0;
        if (args.length >= 4) {
            try {
                seconds = Long.parseLong(args[3]);
            } catch (NumberFormatException e) {
                return List.of("§cNot a number of seconds: " + args[3]);
            }
        }
        String note = core.sessionManager().getRules().getScopes().containsKey(name) ? "" : " §7(rules.yml has no scopes." + name + ": no rules yet)";
        if (keep) {
            if (seconds > 0) return List.of("§cA timed scope cannot be kept: use --keep without seconds.");
            try {
                if (!kept.addScope(handle.uuid(), handle.name(), name)) {
                    note += " §cCould not write players.yml (see the server log)";
                }
            } catch (IllegalArgumentException e) {
                return List.of("§c" + e.getMessage());
            }
            lp.scope(name).expireIn(0);
            return List.of("§aScope " + name + " on for " + handle.name() + " §7(kept: every login)" + note);
        }
        lp.scope(name).expireIn(seconds);
        return List.of("§aScope " + name + " on for " + handle.name() + (seconds > 0 ? " for " + seconds + " s" : "") + note);
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
    public static List<String> suggest(LadsCore core, String[] args, List<String> playerNames) {
        if (args == null || args.length <= 1) {
            String prefix = (args == null || args.length == 0) ? "" : args[0];
            return filterMatching(List.of("reload", "status", "inspect", "set", "clear", "scope"), prefix);
        }

        String sub = args[0].toLowerCase();
        if (args.length == 2) {
            if ("inspect".equals(sub) || "set".equals(sub) || "clear".equals(sub) || "scope".equals(sub)) {
                return filterMatching(playerNames != null ? playerNames : Collections.emptyList(), args[1]);
            }
            return Collections.emptyList();
        }

        if ("set".equals(sub) || "scope".equals(sub)) {
            // --keep may stand anywhere after the player: suggest it once, and complete the rest without it
            String cur = args[args.length - 1];
            List<String> rest = new ArrayList<>();
            boolean hasKeep = false;
            for (int i = 0; i < args.length - 1; i++) {
                if (i >= 2 && "--keep".equalsIgnoreCase(args[i])) hasKeep = true;
                else rest.add(args[i]);
            }
            if (cur.startsWith("--")) return hasKeep ? Collections.emptyList() : filterMatching(List.of("--keep"), cur);
            rest.add(cur);
            args = rest.toArray(new String[0]);
            if ("scope".equals(sub)) {
                if (args.length == 3) {
                    List<String> names = core != null ? new ArrayList<>(core.sessionManager().getRules().getScopes().keySet()) : List.of();
                    return filterMatching(names, cur);
                }
                if (args.length == 4) {
                    List<String> opts = new ArrayList<>(List.of("off"));
                    if (!hasKeep) opts.add("--keep");
                    return filterMatching(opts, cur);
                }
                return Collections.emptyList();
            }
        }

        if ("clear".equals(sub) && args.length == 3) {
            List<String> mods = new ArrayList<>();
            if (core != null && core.schema() != null && core.schema().getModules() != null) {
                for (LadsSchema.SchemaModule mod : core.schema().getModules()) mods.add(mod.getName());
            }
            return filterMatching(mods, args[2]);
        }

        if ("set".equals(sub)) {
            String targetSoFar = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length - 1)).trim();
            String current = args[args.length - 1];
            String fullPrefix = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
            String soFarPrefix = (args.length > 3)
                    ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length - 1)) + " "
                    : "";

            LadsSchema schema = core != null ? core.schema() : null;
            List<String> targets = new ArrayList<>();
            if (schema != null && schema.getModules() != null) {
                for (LadsSchema.SchemaModule mod : schema.getModules()) {
                    targets.add(mod.getName());
                    if (mod.getOptions() != null) {
                        for (LadsSchema.SchemaOption opt : mod.getOptions()) {
                            targets.add(mod.getName() + "." + opt.getName());
                        }
                    }
                }
            }

            Set<String> suggestions = new LinkedHashSet<>();

            // 1. If user is typing module/option name (single or multi-word), suggest next words or matching targets
            String lowerFull = fullPrefix.toLowerCase();
            String lowerSoFar = soFarPrefix.toLowerCase();
            for (String t : targets) {
                if (t.toLowerCase().startsWith(lowerFull)) {
                    if (soFarPrefix.isEmpty()) {
                        suggestions.add(t);
                    } else if (t.toLowerCase().startsWith(lowerSoFar)) {
                        String rem = t.substring(soFarPrefix.length());
                        int end = rem.length();
                        for (int i = current.length(); i < rem.length(); i++) {
                            char c = rem.charAt(i);
                            if (c == ' ' || (c == '.' && i > current.indexOf('.'))) {
                                end = i;
                                break;
                            }
                        }
                        String nextToken = rem.substring(0, end);
                        if (!nextToken.isEmpty()) {
                            suggestions.add(nextToken);
                        }
                    }
                }
            }

            // 2. If text before current word is already a valid schema target, suggest states or option rules
            if (!targetSoFar.isEmpty() && schema != null) {
                ResolvedTarget resolved = resolveTarget(schema, targetSoFar);
                if (resolved != null && resolved.inSchema()) {
                    if (resolved.option() != null) {
                        suggestions.addAll(filterMatching(List.of("max=", "min=", "value="), current));
                    } else {
                        suggestions.addAll(filterMatching(List.of("allow", "block", "on"), current));
                    }
                }
            }

            return new ArrayList<>(suggestions);
        }

        return Collections.emptyList();
    }

    public static List<String> filterMatching(List<String> options, String prefix) {
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