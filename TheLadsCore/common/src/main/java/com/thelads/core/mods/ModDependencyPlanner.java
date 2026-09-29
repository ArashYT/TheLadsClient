package com.thelads.core.mods;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Dependency cascade for enable/disable requests, with the same rules as the launcher's ModStateService.Plan:
 * disabling a mod also disables every requested-enabled mod whose required dependencies would become unsatisfied
 * (counting provides, embedded children of the remaining mods and the platform); enabling a mod also enables its
 * requested-disabled required dependencies, and a required dependency nothing can provide is a blocker.
 * Version ranges and breaks are left to Fabric Loader, which is authoritative at launch.
 */
public final class ModDependencyPlanner {
    /** Always present at launch: the game, Java, Fabric Loader and the MixinExtras it bundles. */
    public static final Set<String> IMPLICIT = Set.of("minecraft", "java", "fabricloader", "mixinextras");
    public static final String CORE_ID = "theladscore";
    public static final String CORE_WARNING = "The in-game Lads menu and all Lads features will be absent until LadsCore is re-enabled in The Lads Launcher.";

    /**
     * A top-level mod. available = it exists (or will be downloaded) at the next launch when enabled; metadataKnown = its
     * dependencies are known (false for entries not downloaded yet); blockedReason != null = it cannot be toggled.
     */
    public record Node(String id, String name, boolean requestedEnabled, boolean available, boolean metadataKnown,
                       List<String> depends, List<String> provides, List<Node> children, String blockedReason) {
        public Node {
            depends = List.copyOf(depends);
            provides = List.copyOf(provides);
            children = List.copyOf(children);
        }
    }

    public record Plan(List<String> targetIds, boolean enable, List<String> alsoDisable, List<String> alsoEnable,
                       List<String> blockers, List<String> warnings) {
        public boolean needsConfirmation() {
            return !alsoDisable.isEmpty() || !alsoEnable.isEmpty() || !blockers.isEmpty() || !warnings.isEmpty();
        }

        /** The next-launch requests a confirmed plan records: the targets, then the cascade. */
        public Map<String, Boolean> requests() {
            Map<String, Boolean> requests = new LinkedHashMap<>();
            targetIds.forEach(id -> requests.put(id, enable));
            alsoDisable.forEach(id -> requests.put(id, false));
            alsoEnable.forEach(id -> requests.put(id, true));
            return requests;
        }
    }

    private ModDependencyPlanner() {}

    public static Plan plan(Collection<Node> nodes, Collection<String> ids, boolean enable) {
        Map<String, Node> byId = new LinkedHashMap<>();
        for (Node node : nodes) byId.putIfAbsent(node.id(), node);
        List<String> targets = List.copyOf(new LinkedHashSet<>(ids));
        List<String> blockers = new ArrayList<>(), warnings = new ArrayList<>();
        List<String> alsoDisable = new ArrayList<>(), alsoEnable = new ArrayList<>();
        for (String id : targets) {
            Node node = byId.get(id);
            if (node == null) blockers.add("Unknown mod id '" + id + "'.");
            else if (node.blockedReason() != null) blockers.add(name(node) + ": " + node.blockedReason());
            else if (enable && !node.available()) blockers.add(name(node) + " is not available for this Minecraft version.");
        }
        Set<String> active = new LinkedHashSet<>();
        for (Node node : byId.values()) if (node.requestedEnabled() && node.available()) active.add(node.id());

        if (!enable) {
            Map<String, Set<String>> missingBefore = new HashMap<>();
            Set<String> providedBefore = provided(active, byId);
            for (String id : active) missingBefore.put(id, missing(byId.get(id), providedBefore));
            active.removeAll(targets);
            for (boolean changed = true; changed; ) {
                changed = false;
                Set<String> provided = provided(active, byId);
                for (String id : List.copyOf(active)) {
                    // Only dependencies this change breaks cascade; an entry that was already unsatisfied is left as it was.
                    if (!missingBefore.get(id).containsAll(missing(byId.get(id), provided))) {
                        active.remove(id);
                        alsoDisable.add(id);
                        changed = true;
                    }
                }
            }
        } else {
            for (String id : targets) if (byId.containsKey(id) && byId.get(id).available()) active.add(id);
            ArrayDeque<String> queue = new ArrayDeque<>(targets);
            while (!queue.isEmpty()) {
                Node node = byId.get(queue.poll());
                if (node == null) continue;
                for (String dependency : required(node)) {
                    if (provided(active, byId).contains(dependency)) continue;
                    Node provider = byId.values().stream()
                        .filter(candidate -> candidate.available() && !active.contains(candidate.id()) && candidate.blockedReason() == null)
                        .filter(candidate -> providedBy(candidate).contains(dependency))
                        .sorted((a, b) -> Boolean.compare(!a.id().equals(dependency), !b.id().equals(dependency)))
                        .findFirst().orElse(null);
                    if (provider == null) {
                        String blocker = name(node) + " requires " + dependency + ", which is not installed and not in the Lads pack for this version.";
                        if (!blockers.contains(blocker)) blockers.add(blocker);
                        continue;
                    }
                    active.add(provider.id());
                    alsoEnable.add(provider.id());
                    queue.add(provider.id());
                }
            }
        }

        // Like the launcher: a disable checks every mod that stays on, an enable only the mods it switches on.
        List<String> checked = enable ? targets.stream().filter(active::contains).toList() : List.copyOf(active);
        long unknown = Stream.concat(checked.stream(), alsoEnable.stream()).map(byId::get)
            .filter(node -> node != null && !node.metadataKnown()).count();
        if (unknown > 0)
            warnings.add(unknown + (unknown == 1 ? " mod is" : " mods are") + " not downloaded yet: their dependencies are checked at next launch.");
        if (!enable && (targets.contains(CORE_ID) || alsoDisable.contains(CORE_ID))) warnings.add(CORE_WARNING);
        return new Plan(targets, enable, List.copyOf(alsoDisable), List.copyOf(alsoEnable), List.copyOf(blockers), List.copyOf(warnings));
    }

    private static String name(Node node) {
        return node.name() == null || node.name().isBlank() ? node.id() : node.name();
    }

    /** Ids (and provided aliases) a mod brings, including every embedded child. */
    private static Set<String> providedBy(Node node) {
        Set<String> result = new HashSet<>();
        result.add(node.id());
        result.addAll(node.provides());
        for (Node child : node.children()) result.addAll(providedBy(child));
        return result;
    }

    private static Set<String> provided(Set<String> active, Map<String, Node> byId) {
        Set<String> result = new HashSet<>(IMPLICIT);
        for (String id : active) result.addAll(providedBy(byId.get(id)));
        return result;
    }

    /** Required dependencies of a mod and of the jars embedded in it. */
    private static Set<String> required(Node node) {
        Set<String> result = new LinkedHashSet<>(node.depends());
        for (Node child : node.children()) result.addAll(required(child));
        return result;
    }

    private static Set<String> missing(Node node, Set<String> provided) {
        Set<String> result = new HashSet<>(required(node));
        result.removeAll(provided);
        return result;
    }
}
