package com.thelads.core.mods;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.util.ClientPaths;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.ModuleSupport;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In-game mod inventory: what Fabric loaded in this process, the launcher's snapshot (disabled, pending, unavailable,
 * ownership, upstream names), the explicit next-launch requests in lads-mod-state.json, and the live Lads modules.
 */
public final class ModInventoryModel {
    public static final String VERIFY_PROPERTY = "thelads.verifyModInventory";
    public static final String REQUEST_PROPERTY = "thelads.verifyModRequest";
    private static final AtomicBoolean REQUEST_DONE = new AtomicBoolean();
    public static final String PLATFORM_REASON = "Minecraft, Java and Fabric Loader are platform components, not mods you can toggle.";
    private static final Logger LOG = LoggerFactory.getLogger("TheLadsCore");
    private static final AtomicBoolean VIEW_LOGGED = new AtomicBoolean(), TITLE_LOGGED = new AtomicBoolean();
    /** Entries that are neither on disk nor downloaded at the next launch; every other status is a real (or pending) jar. */
    private static final Set<String> UNAVAILABLE = Set.of("unavailable", "retiredCopy");
    private static final Set<String> NOT_THIRD_PARTY = Set.of("core", "nativeModule", "platform", "embedded");
    public static final String RUNTIME_REASON = "Loaded at runtime by another mod, not from a jar in mods; disable the mod that loads it.";

    /** Same filters as the launcher's Mods page (ModInventoryView.Filter). */
    public enum Filter {
        ALL("All"), LADS("Lads modules"), THIRD_PARTY("Third-party"), ENABLED("Enabled"), DISABLED("Disabled"), LIBRARIES("Libraries & dependencies");
        private final String label;
        Filter(String label) { this.label = label; }
        public String label() { return label; }
    }

    /**
     * One inventory row. requested is the next-launch state, loaded whether Fabric loaded it in this process (for Lads modules:
     * on right now). available = it exists or will be downloaded at the next launch; requested is false when it is not.
     * ownership null = a loaded mod while there is no launcher snapshot; "runtime" = a loaded mod the snapshot does not list
     * (another mod loaded it at runtime, e.g. Essential's libraries). rootId is the top-level mod that carries an embedded row.
     * enabledOnDisk is null unless the row is a jar in mods (then false for a .disabled copy).
     */
    public record Row(String id, String name, String upstreamName, String version, String ownership, String status,
                      boolean loaded, boolean requested, boolean available, Boolean enabledOnDisk, boolean restartRequired, boolean canToggle,
                      String blockedReason, boolean library, String note, String parentId, String rootId, String projectId,
                      boolean metadataKnown, List<String> depends, List<String> provides, List<Row> children) {
        public boolean nativeModule() { return "nativeModule".equals(ownership); }
        public boolean embedded() { return parentId != null; }
        public String displayName() { return upstreamName != null && !upstreamName.isBlank() ? upstreamName : name; }
        /** Unique across mods and Lads modules, whose names could equal a mod id. */
        public String key() { return (nativeModule() ? "module/" : "mod/") + id; }
    }

    private final String minecraftVersion;
    private final boolean snapshotPresent;
    private final List<Row> rows;
    private final String notice;
    /** Ids some entry depends on: their providers belong under "Libraries &amp; dependencies", as in the launcher. */
    private final Set<String> needed;

    private ModInventoryModel(String minecraftVersion, boolean snapshotPresent, List<Row> rows, String notice) {
        this.minecraftVersion = minecraftVersion;
        this.snapshotPresent = snapshotPresent;
        this.rows = List.copyOf(rows);
        this.notice = notice;
        this.needed = flatten().stream().flatMap(row -> row.depends().stream()).collect(Collectors.toUnmodifiableSet());
    }

    public List<Row> rows() { return rows; }
    public String minecraftVersion() { return minecraftVersion; }
    /** Why some entries may be missing or preferences ignored (no launcher snapshot, damaged state file); null when complete. */
    public String notice() { return notice; }

    /** Builds the model for this profile from the live game. */
    public static ModInventoryModel load() {
        Path directory = ClientPaths.getBaseDir();
        return build(LadsGameBridge.get().loadedMods(), InventorySnapshot.read(directory), new ModStateStore(directory).read(),
            ModuleManager.getInstance().getModules());
    }

    public static ModInventoryModel build(List<LoadedMod> loaded, InventorySnapshot snapshot, ModStateStore.State state,
                                          Collection<Module> modules) {
        Assembler assembler = new Assembler(loaded, state, modules, snapshot.present());
        List<Row> top = new ArrayList<>();
        for (InventorySnapshot.Entry entry : snapshot.entries()) {
            // Live ModuleSupport below is authoritative in game; the snapshot's module rows are the launcher's copy of the catalog.
            if (!"nativeModule".equals(entry.ownership())) top.add(assembler.entry(entry, null, null, true));
        }
        for (LoadedMod mod : loaded) {
            boolean topLevel = mod.parentId() == null || !assembler.loadedById.containsKey(mod.parentId());
            if (topLevel && !assembler.seen.contains(mod.id())) top.add(assembler.loaded(mod, null, null, true, null));
        }
        for (Module module : modules) {
            String name = module.getName(), support = ModuleSupport.support(name);
            if (support.equals("external")) continue;
            boolean builtIn = support.equals("builtIn"), toggleable = ModuleSupport.isToggleable(name);
            String reason = toggleable ? null : builtIn ? "Discord Rich Presence is coming soon." : ModuleSupport.get(name).description();
            top.add(new Row(name, name, null, null, "nativeModule", builtIn ? "installed" : "unavailable", builtIn && module.isEnabled(),
                module.isEnabled(), builtIn, null, false, toggleable, reason, false, null, null, name, null, true, List.of(), List.of(), List.of()));
        }
        top.sort(Comparator.comparingInt(ModInventoryModel::rank).thenComparing(Row::displayName, String.CASE_INSENSITIVE_ORDER));
        String minecraft = snapshot.minecraftVersion() != null ? snapshot.minecraftVersion()
            : assembler.loadedById.containsKey("minecraft") ? assembler.loadedById.get("minecraft").version() : null;
        String notice = Stream.of(snapshot.notice(), state.error()).filter(Objects::nonNull).collect(Collectors.joining(" "));
        return new ModInventoryModel(minecraft, snapshot.present(), top, notice.isEmpty() ? null : notice);
    }

    private static int rank(Row row) {
        if (row.ownership() == null) return 1;
        return switch (row.ownership()) {
            case "core" -> 0;
            case "nativeModule" -> 2;
            case "platform" -> 3;
            default -> 1;
        };
    }

    /** Merges one profile's sources while tracking which ids already have a row. */
    private static final class Assembler {
        final Map<String, LoadedMod> loadedById = new LinkedHashMap<>();
        final Map<String, List<LoadedMod>> loadedChildren = new HashMap<>();
        final Set<String> seen = new HashSet<>();
        final Map<String, List<String>> integrationNotes = new HashMap<>(), replacementNotes = new HashMap<>();
        final ModStateStore.State state;
        final boolean snapshotPresent;

        Assembler(List<LoadedMod> loaded, ModStateStore.State state, Collection<Module> modules, boolean snapshotPresent) {
            this.state = state;
            this.snapshotPresent = snapshotPresent;
            for (LoadedMod mod : loaded) {
                loadedById.putIfAbsent(mod.id(), mod);
                if (mod.parentId() != null) loadedChildren.computeIfAbsent(mod.parentId(), k -> new ArrayList<>()).add(mod);
            }
            for (Module module : modules) {
                String modId = ModuleSupport.externalModId(module.getName()), support = ModuleSupport.support(module.getName());
                if (modId == null) continue;
                // A wrapper stays an upstream mod with a Lads integration; a built-in replacement explains a missing upstream entry.
                // Same wording as the launcher's notes, so a note it already wrote into the snapshot is not repeated.
                if (support.equals("external")) integrationNotes.computeIfAbsent(modId, k -> new ArrayList<>()).add("Lads integration: " + module.getName());
                else if (support.equals("builtIn")) replacementNotes.computeIfAbsent(modId, k -> new ArrayList<>()).add("Lads provides its own " + module.getName() + " instead");
            }
        }

        Row entry(InventorySnapshot.Entry e, String parentId, String rootId, boolean parentRequested) {
            seen.add(e.id());
            LoadedMod mod = loadedById.get(e.id());
            boolean top = parentId == null, platform = "platform".equals(e.ownership());
            boolean available = e.status() == null ? mod != null : !UNAVAILABLE.contains(e.status());
            Boolean explicit = top && !platform ? state.enabled(e.id(), e.projectId()) : null;
            // Unavailable and retired entries never load at the next launch, whatever a saved key says (the launcher agrees).
            boolean requested = available && (top ? explicit != null ? explicit : e.requestedEnabled() : parentRequested);
            String name = first(e.displayName(), mod == null ? null : mod.name(), e.id()), root = top ? e.id() : rootId;
            List<Row> children = new ArrayList<>();
            for (InventorySnapshot.Entry child : e.children()) children.add(entry(child, e.id(), root, requested));
            children.addAll(loadedChildren(e.id(), root, requested, name));
            children.sort(Comparator.comparing(Row::displayName, String.CASE_INSENSITIVE_ORDER));
            List<String> depends = !e.depends().isEmpty() || mod == null ? e.depends() : mod.depends();
            List<String> provides = !e.provides().isEmpty() || mod == null ? e.provides() : mod.provides();
            List<String> notes = new ArrayList<>();
            if (e.note() != null) notes.add(e.note());
            notes.addAll(integrationNotes.getOrDefault(e.id(), List.of()));
            if ("unavailable".equals(e.status())) notes.addAll(replacementNotes.getOrDefault(e.id(), List.of()));
            // An invalid jar is never loaded by Fabric, so a restart would not change it.
            boolean restart = !platform && available && !"invalid".equals(e.status()) && requested != (mod != null);
            return new Row(e.id(), name, e.upstreamName(), first(e.version(), mod == null ? null : mod.version()), e.ownership(), e.status(),
                mod != null, requested, available, top && e.filePath() != null ? e.enabledOnDisk() : null, restart, e.canToggle(),
                e.canToggle() ? null : e.toggleBlockedReason(), e.isLibrary(), join(notes), parentId, root, e.projectId(),
                e.dependenciesKnown(), depends, provides, children);
        }

        Row loaded(LoadedMod mod, String parentId, String rootId, boolean parentRequested, String parentName) {
            seen.add(mod.id());
            boolean top = parentId == null, platform = top && "builtin".equals(mod.kind()), core = ModDependencyPlanner.CORE_ID.equals(mod.id());
            // The launcher lists every jar in mods, so a loaded mod its snapshot lacks was loaded at runtime by another mod:
            // a request for it could never be applied.
            boolean runtime = top && !platform && !core && snapshotPresent;
            String ownership = !top ? "embedded" : core ? "core" : platform ? "platform" : runtime ? "runtime" : null;
            Boolean explicit = top && !platform && !runtime ? state.enabled(mod.id(), null) : null;
            // It is loaded, so without an explicit request its disk state (enabled) stays.
            boolean requested = top ? explicit == null || explicit : parentRequested;
            String name = first(mod.name(), mod.id()), root = top ? mod.id() : rootId;
            List<Row> children = loadedChildren(mod.id(), root, requested, name);
            children.sort(Comparator.comparing(Row::displayName, String.CASE_INSENSITIVE_ORDER));
            String reason = platform ? PLATFORM_REASON : runtime ? RUNTIME_REASON
                : top ? null : "Embedded inside " + parentName + "; disable " + parentName + " to remove it.";
            // Without a snapshot a loaded top-level mod is the best evidence of an enabled jar in mods.
            Boolean enabledOnDisk = top && !platform && !runtime ? Boolean.TRUE : null;
            return new Row(mod.id(), name, null, mod.version(), ownership, top ? "installed" : "embedded", true, requested, true, enabledOnDisk,
                !platform && !requested, top && !platform && !runtime, reason, !top || mod.libraryBadge(),
                join(integrationNotes.getOrDefault(mod.id(), List.of())), parentId, root, null, true, mod.depends(), mod.provides(), children);
        }

        private List<Row> loadedChildren(String parentId, String rootId, boolean parentRequested, String parentName) {
            List<Row> result = new ArrayList<>();
            for (LoadedMod child : loadedChildren.getOrDefault(parentId, List.of()))
                if (!seen.contains(child.id())) result.add(loaded(child, parentId, rootId, parentRequested, parentName));
            return result;
        }
    }

    private static String first(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return null;
    }

    /** Joins notes, skipping one the launcher already wrote into the snapshot note. */
    private static String join(List<String> parts) {
        String joined = "";
        for (String part : parts)
            if (part != null && !part.isBlank() && !joined.contains(part))
                joined = joined.isEmpty() ? part : joined + (joined.endsWith(".") ? " " : ". ") + part;
        return joined.isEmpty() ? null : joined;
    }

    /**
     * The launcher's rules: Enabled/Disabled cover mods and Lads modules, not the platform or embedded libraries;
     * Libraries &amp; dependencies adds every entry whose id or provided id another entry depends on.
     */
    public boolean matches(Row row, Filter filter) {
        boolean platform = "platform".equals(row.ownership()), modOrModule = !platform && !row.embedded();
        return switch (filter) {
            case ALL -> true;
            case LADS -> "core".equals(row.ownership()) || row.nativeModule();
            case THIRD_PARTY -> row.ownership() == null || !NOT_THIRD_PARTY.contains(row.ownership());
            case ENABLED -> modOrModule && row.available() && row.requested();
            case DISABLED -> modOrModule && row.available() && !row.requested();
            case LIBRARIES -> !platform && (row.library() || needed.contains(row.id()) || row.provides().stream().anyMatch(needed::contains));
        };
    }

    /** Search covers the display name, the upstream name and the mod id. */
    public static boolean matchesSearch(Row row, String query) {
        if (query == null || query.isBlank()) return true;
        String q = query.strip().toLowerCase(Locale.ROOT);
        return Stream.of(row.name(), row.upstreamName(), row.id()).anyMatch(text -> text != null && text.toLowerCase(Locale.ROOT).contains(q));
    }

    private boolean selfMatches(Row row, Filter filter, String query) {
        return matches(row, filter) && matchesSearch(row, query);
    }

    private boolean shows(Row row, Filter filter, String query) {
        return selfMatches(row, filter, query) || row.children().stream().anyMatch(child -> shows(child, filter, query));
    }

    /** Top-level rows to show: a row is shown when it or any embedded descendant matches, so libraries never disappear. */
    public List<Row> visible(Filter filter, String query) {
        return rows.stream().filter(row -> shows(row, filter, query)).toList();
    }

    /**
     * Parents with a matching child open automatically for a search or the Libraries filter (children are what they look for);
     * other filters open only parents shown because of a child, so "Enabled" does not unfold every jar-in-jar.
     */
    public boolean autoExpanded(Row row, Filter filter, String query) {
        boolean childMatches = row.children().stream().anyMatch(child -> shows(child, filter, query));
        return childMatches && (filter == Filter.LIBRARIES || query != null && !query.isBlank() || !selfMatches(row, filter, query));
    }

    public List<Row> flatten() {
        List<Row> result = new ArrayList<>();
        for (Row row : rows) addFlat(row, result);
        return result;
    }

    private static void addFlat(Row row, List<Row> result) {
        result.add(row);
        for (Row child : row.children()) addFlat(child, result);
    }

    /** Rows per filter, children included, ignoring the search text. */
    public Map<Filter, Integer> counts() {
        Map<Filter, Integer> counts = new EnumMap<>(Filter.class);
        List<Row> all = flatten();
        for (Filter filter : Filter.values()) counts.put(filter, (int) all.stream().filter(row -> matches(row, filter)).count());
        return counts;
    }

    /**
     * The launcher's counts line (ModInventoryCounts) for the same snapshot: enabled and disabled jars in mods, pending
     * downloads and mods unavailable for this version, all top-level; Lads modules and embedded libraries are counted apart.
     */
    public record FileCounts(int enabled, int disabled, int pending, int unavailable, int ladsModules, int embedded) {
        public String text() {
            return enabled + " enabled · " + disabled + " disabled · " + pending + " pending · " + unavailable + " unavailable · "
                + ladsModules + " Lads modules · " + embedded + " embedded libraries";
        }
    }

    public FileCounts fileCounts() {
        return new FileCounts(count(rows, row -> Boolean.TRUE.equals(row.enabledOnDisk())),
            count(rows, row -> Boolean.FALSE.equals(row.enabledOnDisk())),
            count(rows, row -> "pendingDownload".equals(row.status())),
            count(rows, row -> "unavailable".equals(row.status()) && !row.nativeModule()),
            count(rows, Row::nativeModule), count(flatten(), Row::embedded));
    }

    private static int count(List<Row> rows, Predicate<Row> predicate) {
        return (int) rows.stream().filter(predicate).count();
    }

    public Row find(String key) {
        return flatten().stream().filter(row -> row.key().equals(key)).findFirst().orElse(null);
    }

    /** Dependency plan over the top-level mods (Lads modules are not Fabric mods and never cascade). */
    public ModDependencyPlanner.Plan plan(Collection<String> ids, boolean enable) {
        List<ModDependencyPlanner.Node> nodes = rows.stream().filter(row -> !row.nativeModule()).map(ModInventoryModel::node).toList();
        return ModDependencyPlanner.plan(nodes, ids, enable);
    }

    private static ModDependencyPlanner.Node node(Row row) {
        return new ModDependencyPlanner.Node(row.id(), row.displayName(), row.requested(), row.available(), row.metadataKnown(),
            row.depends(), row.provides(), row.children().stream().map(ModInventoryModel::node).toList(),
            row.canToggle() ? null : Objects.requireNonNullElse(row.blockedReason(), "It cannot be toggled here."));
    }

    public Map<String, String> projectIds() {
        Map<String, String> result = new HashMap<>();
        for (Row row : rows) if (row.projectId() != null) result.put(row.id(), row.projectId());
        return result;
    }

    public static String ownershipLabel(Row row) {
        if (row.ownership() == null) return "Third-party";
        return switch (row.ownership()) {
            case "core" -> "Lads";
            case "nativeModule" -> "Lads module";
            case "pack" -> "Third-party";
            case "user" -> "User";
            case "embedded" -> "Library";
            case "runtime" -> "Third-party";
            case "platform" -> "Platform";
            case "retired" -> "Removed from pack";
            default -> row.ownership();
        };
    }

    public String statusLabel(Row row) {
        if (row.nativeModule()) return row.available() ? "Built in" : "Unavailable";
        if (row.status() == null) return "Installed";
        return switch (row.status()) {
            case "installed" -> "Installed";
            case "disabled" -> "Disabled";
            case "pendingDownload" -> "Pending download";
            case "notDownloaded" -> "Not downloaded";
            case "unavailable" -> minecraftVersion == null ? "Unavailable" : "Unavailable for " + minecraftVersion;
            case "unsupported" -> "Unsupported";
            case "embedded" -> "Embedded";
            case "retiredCopy" -> "Removed from pack";
            case "invalid" -> "Invalid";
            default -> row.status();
        };
    }

    /** Machine-readable QA line: every row and the per-filter counts. */
    public String toJson(String source) {
        JsonObject root = new JsonObject();
        root.addProperty("source", source);
        root.addProperty("minecraftVersion", minecraftVersion);
        root.addProperty("launcherSnapshot", snapshotPresent);
        JsonArray array = new JsonArray();
        for (Row row : flatten()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", row.id());
            o.addProperty("ownership", row.ownership());
            o.addProperty("status", row.status());
            o.addProperty("loaded", row.loaded());
            o.addProperty("requested", row.requested());
            o.addProperty("restartRequired", row.restartRequired());
            o.addProperty("parentId", row.parentId());
            array.add(o);
        }
        root.add("rows", array);
        JsonObject counts = new JsonObject();
        counts().forEach((filter, count) -> counts.addProperty(filter.label(), count));
        root.add("counts", counts);
        FileCounts files = fileCounts();
        JsonObject fileCounts = new JsonObject();
        fileCounts.addProperty("enabled", files.enabled());
        fileCounts.addProperty("disabled", files.disabled());
        fileCounts.addProperty("pending", files.pending());
        fileCounts.addProperty("unavailable", files.unavailable());
        fileCounts.addProperty("ladsModules", files.ladsModules());
        fileCounts.addProperty("embedded", files.embedded());
        root.add("fileCounts", fileCounts);
        return new GsonBuilder().serializeNulls().create().toJson(root);
    }

    /** Logs the first Mods view model of this process when -Dthelads.verifyModInventory=true. */
    public void logFirstViewForQa() {
        if (Boolean.getBoolean(VERIFY_PROPERTY) && VIEW_LOGGED.compareAndSet(false, true))
            LOG.info("Lads mods inventory snapshot: {}", toJson("view"));
    }

    /**
     * QA only, from the title screen hooks: with -Dthelads.verifyModRequest=&lt;mod id&gt;:&lt;true|false&gt;, records that request once
     * per process exactly as a confirmed Installed mods toggle does (same plan and cascade, one locked write, source "game").
     */
    public static void requestAtFirstTitleScreen() {
        String spec = System.getProperty(REQUEST_PROPERTY);
        if (spec == null || spec.isBlank() || !REQUEST_DONE.compareAndSet(false, true)) return;
        try {
            LOG.info("Lads mod request probe END: 1 passed, 0 failed; {}", recordRequestForQa(spec, ClientPaths.getBaseDir(), load()));
        } catch (Exception | LinkageError failure) {
            LOG.error("Lads mod request probe FAILED after 0 checks", failure);
        }
    }

    /** Records one "&lt;id&gt;:&lt;true|false&gt;" request through the toggle's path and returns "&lt;id&gt; -&gt; &lt;bool&gt;". */
    public static String recordRequestForQa(String spec, Path gameDirectory, ModInventoryModel model) throws java.io.IOException {
        int colon = spec.lastIndexOf(':');
        String id = colon > 0 ? spec.substring(0, colon).trim() : "", value = colon > 0 ? spec.substring(colon + 1).trim() : "";
        if (id.isEmpty() || !(value.equals("true") || value.equals("false")))
            throw new IllegalArgumentException(REQUEST_PROPERTY + " must be <mod id>:<true|false>, not '" + spec + "'");
        boolean enable = Boolean.parseBoolean(value);
        Row row = model.find("mod/" + id);
        if (row == null) throw new IllegalStateException(id + " is not a mod in the in-game inventory");
        if (!row.canToggle()) throw new IllegalStateException(id + " cannot be toggled in game: " + row.blockedReason());
        ModDependencyPlanner.Plan plan = model.plan(List.of(id), enable);
        LOG.info("Lads mod request probe: plan targets {}, also disable {}, also enable {}, warnings {}, blockers {}",
            plan.targetIds(), plan.alsoDisable(), plan.alsoEnable(), plan.warnings(), plan.blockers());
        if (!plan.blockers().isEmpty()) throw new IllegalStateException("the plan has blockers: " + plan.blockers());
        ModStateStore store = new ModStateStore(gameDirectory);
        store.setRequested(plan.requests(), model.projectIds());
        Boolean saved = store.read().enabled(id, null);
        if (saved == null || saved != enable) throw new IllegalStateException(store.file() + " does not hold " + id + " -> " + enable);
        return id + " -> " + enable;
    }

    /** Called from the title screen hooks; logs once per process when -Dthelads.verifyModInventory=true. */
    public static void logAtFirstTitleScreen() {
        if (!Boolean.getBoolean(VERIFY_PROPERTY) || !TITLE_LOGGED.compareAndSet(false, true)) return;
        try {
            LOG.info("Lads mods inventory snapshot: {}", load().toJson("title"));
        } catch (RuntimeException failure) {
            // A QA probe must never break the title screen; the missing line fails the harness instead.
            LOG.error("Lads mods inventory snapshot FAILED", failure);
        }
    }
}
