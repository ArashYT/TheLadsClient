package com.thelads.ladsapi.session;

import com.thelads.ladsapi.rules.LadsRules;
import com.thelads.ladsapi.rules.ModuleRule;
import com.thelads.ladsapi.rules.RuleOption;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Rules and scopes an admin saved for a player with {@code /ladsapi set <player> ... --keep}: players.yml, keyed by UUID.
 * They are read live when a policy is built (so they sit in the same top layer as the API overrides, under them) and the
 * scopes are re-entered whenever the player's session is created. rules.yml is never touched.
 */
public final class KeptSettings {
    static final int MAX_PLAYERS = 20_000;
    static final int MAX_MODULES = 64;
    static final int MAX_OPTIONS = 64;
    static final int MAX_SCOPES = 16;
    static final int MAX_TEXT = 256;
    private static final Pattern NAME = Pattern.compile("[\\p{L}\\p{N} ._+\\-:()]{1,64}");
    private static final Pattern SCOPE = Pattern.compile("[a-z0-9_.\\-]{1,32}");
    private static final Pattern STATE = Pattern.compile("allow|block|on|inherit");

    private static final class Entry {
        String name = "";
        final Map<String, ModuleRule> rules = new LinkedHashMap<>();
        final Set<String> scopes = new LinkedHashSet<>();

        boolean isEmpty() { return rules.isEmpty() && scopes.isEmpty(); }
    }

    private final Path file;
    private final LadsRules rulesParser;
    private final Logger logger;
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();

    public KeptSettings(Path dataFolder, LadsRules rulesParser, Logger logger) {
        this.file = dataFolder.resolve("players.yml");
        this.rulesParser = rulesParser;
        this.logger = logger;
        load();
    }

    // ---- reading ----

    /** A module or option name that may be stored. */
    public static boolean validName(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    /** A scope name that may be kept (already lower case). */
    public static boolean validScope(String scope) {
        return scope != null && SCOPE.matcher(scope).matches();
    }

    public synchronized Map<String, ModuleRule> rules(UUID uuid) {
        Entry e = entries.get(uuid);
        Map<String, ModuleRule> copy = new LinkedHashMap<>();
        if (e != null) for (Map.Entry<String, ModuleRule> r : e.rules.entrySet()) copy.put(r.getKey(), ModuleRule.copyOf(r.getValue()));
        return copy;
    }

    public synchronized List<String> scopes(UUID uuid) {
        Entry e = entries.get(uuid);
        return e == null ? List.of() : new ArrayList<>(e.scopes);
    }

    public synchronized boolean has(UUID uuid) {
        Entry e = entries.get(uuid);
        return e != null && !e.isEmpty();
    }

    /** The UUID of a kept player by last known name or by UUID text; null when there is none. */
    public synchronized UUID find(String nameOrUuid) {
        if (nameOrUuid == null) return null;
        for (Map.Entry<UUID, Entry> e : entries.entrySet()) {
            if (e.getKey().toString().equalsIgnoreCase(nameOrUuid) || e.getValue().name.equalsIgnoreCase(nameOrUuid)) return e.getKey();
        }
        return null;
    }

    public synchronized String name(UUID uuid) {
        Entry e = entries.get(uuid);
        return e == null ? "" : e.name;
    }

    // ---- changing (each call saves; false = could not save, the change is still in memory) ----

    /** Adds the rule of one module on top of what is kept for the player (an option's set fields replace the old ones). */
    public synchronized boolean put(UUID uuid, String name, String module, ModuleRule rule) {
        if (!validName(module)) throw new IllegalArgumentException("Bad module name: " + module);
        for (String option : rule.getOptions().keySet()) {
            if (!validName(option)) throw new IllegalArgumentException("Bad option name: " + option);
        }
        Entry e = entries.get(uuid);
        if (e == null) {
            if (entries.size() >= MAX_PLAYERS) throw new IllegalArgumentException("Too many players in players.yml");
            e = new Entry();
        }
        String existing = key(e.rules, module);
        if (existing == null && e.rules.size() >= MAX_MODULES) throw new IllegalArgumentException("Too many kept modules for one player");
        ModuleRule target = e.rules.computeIfAbsent(existing == null ? module : existing, k -> new ModuleRule());
        target.mergeFrom(rule);
        if (target.getOptions().size() > MAX_OPTIONS) {
            e.rules.remove(existing == null ? module : existing);
            throw new IllegalArgumentException("Too many kept options for one module");
        }
        e.name = name == null ? e.name : name;
        entries.put(uuid, e);
        return save();
    }

    /** Keeps a scope (lower case name). */
    public synchronized boolean addScope(UUID uuid, String name, String scope) {
        if (!validScope(scope)) throw new IllegalArgumentException("Bad scope name: " + scope);
        Entry e = entries.get(uuid);
        if (e == null) {
            if (entries.size() >= MAX_PLAYERS) throw new IllegalArgumentException("Too many players in players.yml");
            e = new Entry();
        }
        if (!e.scopes.contains(scope) && e.scopes.size() >= MAX_SCOPES) throw new IllegalArgumentException("Too many kept scopes for one player");
        e.scopes.add(scope);
        e.name = name == null ? e.name : name;
        entries.put(uuid, e);
        return save();
    }

    /** Drops a kept scope; true when it was kept. */
    public synchronized boolean removeScope(UUID uuid, String scope) {
        Entry e = entries.get(uuid);
        if (e == null || !e.scopes.remove(scope)) return false;
        if (e.isEmpty()) entries.remove(uuid);
        save();
        return true;
    }

    /** Drops the kept rules of one module (case-insensitive; null = all rules and all scopes); true when something went. */
    public synchronized boolean clear(UUID uuid, String module) {
        Entry e = entries.get(uuid);
        if (e == null) return false;
        boolean changed;
        if (module == null) {
            changed = !e.isEmpty();
            e.rules.clear();
            e.scopes.clear();
        } else {
            String k = key(e.rules, module);
            changed = k != null;
            if (changed) e.rules.remove(k);
        }
        if (e.isEmpty()) entries.remove(uuid);
        if (changed) save();
        return changed;
    }

    /** Remembers the name a kept player has now (the file shows who a UUID is). */
    public synchronized void touchName(UUID uuid, String name) {
        Entry e = entries.get(uuid);
        if (e != null && name != null && !name.equals(e.name)) {
            e.name = name;
            save();
        }
    }

    private static String key(Map<String, ?> map, String name) {
        for (String k : map.keySet()) if (k.equalsIgnoreCase(name)) return k;
        return null;
    }

    // ---- file ----

    /** (Re)reads players.yml; a broken file keeps what is in memory. */
    @SuppressWarnings("unchecked")
    public synchronized void load() {
        if (!Files.isRegularFile(file)) {
            entries.clear();
            return;
        }
        Map<UUID, Entry> fresh = new LinkedHashMap<>();
        try (InputStream in = Files.newInputStream(file)) {
            Object root = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
            Object players = root instanceof Map<?, ?> m ? m.get("players") : null;
            if (players instanceof Map<?, ?> pm) {
                for (Map.Entry<?, ?> pe : pm.entrySet()) {
                    if (fresh.size() >= MAX_PLAYERS) break;
                    UUID uuid;
                    try {
                        uuid = UUID.fromString(String.valueOf(pe.getKey()));
                    } catch (IllegalArgumentException bad) {
                        warn("players.yml: skipped '" + String.valueOf(pe.getKey()).replaceAll("[^\\x20-\\x7E]", "?") + "' (not a UUID)");
                        continue;
                    }
                    if (!(pe.getValue() instanceof Map<?, ?> body)) continue;
                    Entry e = new Entry();
                    Object n = body.get("name");
                    if (n instanceof String s && s.length() <= 64) e.name = s;
                    readRules(body.get("rules"), e, uuid);
                    Object sc = body.get("scopes");
                    if (sc instanceof List<?> list) {
                        for (Object o : list) {
                            String s = String.valueOf(o).toLowerCase(Locale.ROOT);
                            if (validScope(s) && e.scopes.size() < MAX_SCOPES) e.scopes.add(s);
                            else warn("players.yml: " + uuid + " skipped scope '" + s.replaceAll("[^\\x20-\\x7E]", "?") + "'");
                        }
                    }
                    if (!e.isEmpty()) fresh.put(uuid, e);
                }
            }
        } catch (Exception ex) {
            warn("Error reading players.yml, keeping the settings in memory: " + ex);
            return;
        }
        entries.clear();
        entries.putAll(fresh);
    }

    /** Rules go through the rules.yml parser, then only what rules.yml could never hold sanely is dropped. */
    private void readRules(Object data, Entry e, UUID uuid) {
        if (!(data instanceof Map<?, ?> raw)) return;
        Map<String, Object> clean = new LinkedHashMap<>();
        for (Map.Entry<?, ?> me : raw.entrySet()) {
            String module = String.valueOf(me.getKey());
            if (!validName(module) || clean.size() >= MAX_MODULES) {
                warn("players.yml: " + uuid + " skipped module '" + module.replaceAll("[^\\x20-\\x7E]", "?") + "'");
                continue;
            }
            clean.put(module, me.getValue());
        }
        for (Map.Entry<String, ModuleRule> pe : rulesParser.parseLayer(clean, logger).entrySet()) {
            ModuleRule rule = pe.getValue();
            if (rule.getState() != null && !STATE.matcher(rule.getState()).matches()) rule.setState(null);
            rule.getOptions().entrySet().removeIf(oe -> !validName(oe.getKey()) || !sane(oe.getValue()));
            if (rule.getOptions().size() > MAX_OPTIONS) continue;
            if (rule.getState() != null || !rule.getOptions().isEmpty()) e.rules.put(pe.getKey(), rule);
        }
    }

    private static boolean sane(RuleOption o) {
        if (o == null || o.isEmpty()) return false;
        Object v = o.getValue();
        if (v != null && !(v instanceof Boolean) && !(v instanceof Number) && !(v instanceof String)) return false;
        if (v instanceof String s && s.length() > MAX_TEXT) return false;
        if (v instanceof Double d && (d.isNaN() || d.isInfinite())) return false;
        if (o.getChoices() != null) {
            if (o.getChoices().size() > 64) return false;
            for (String c : o.getChoices()) if (c == null || c.length() > MAX_TEXT) return false;
        }
        return true;
    }

    private boolean save() {
        Map<String, Object> players = new LinkedHashMap<>();
        for (Map.Entry<UUID, Entry> pe : entries.entrySet()) {
            Entry e = pe.getValue();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("name", e.name);
            if (!e.rules.isEmpty()) {
                Map<String, Object> rules = new LinkedHashMap<>();
                for (Map.Entry<String, ModuleRule> re : e.rules.entrySet()) rules.put(re.getKey(), toPlain(re.getValue()));
                body.put("rules", rules);
            }
            if (!e.scopes.isEmpty()) body.put("scopes", new ArrayList<>(e.scopes));
            players.put(pe.getKey().toString(), body);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("players", players);
        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        Path tmp = file.resolveSibling("players.yml.tmp");
        try {
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                w.write("# LadsClientAPI: settings kept per player with /ladsapi set <player> ... --keep. Edit with /ladsapi, then /ladsapi reload.\n");
                new Yaml(opts).dump(root, w);
            }
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            warn("Could not save players.yml: " + e);
            return false;
        }
    }

    private static Map<String, Object> toPlain(ModuleRule rule) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (rule.getState() != null) m.put("state", rule.getState());
        for (Map.Entry<String, RuleOption> oe : rule.getOptions().entrySet()) {
            RuleOption o = oe.getValue();
            Map<String, Object> om = new LinkedHashMap<>();
            if (o.getValue() != null) om.put("value", o.getValue());
            if (o.getMin() != null) om.put("min", o.getMin());
            if (o.getMax() != null) om.put("max", o.getMax());
            if (o.getChoices() != null) om.put("choices", new ArrayList<>(o.getChoices()));
            if (o.getLock() != null) om.put("lock", o.getLock());
            m.put(oe.getKey(), om);
        }
        return m;
    }

    private void warn(String msg) {
        if (logger != null) logger.warning("[LadsClientAPI] " + msg);
    }
}
