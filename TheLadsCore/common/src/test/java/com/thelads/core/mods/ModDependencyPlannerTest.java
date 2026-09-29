package com.thelads.core.mods;

import com.thelads.core.mods.ModDependencyPlanner.Node;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModDependencyPlannerTest {
    private static Node mod(String id, boolean enabled, String... depends) {
        return new Node(id, id, enabled, true, true, List.of(depends), List.of(), List.of(), null);
    }
    private static Node library(String id, boolean enabled, List<String> provides, Node... children) {
        return new Node(id, id, enabled, true, true, List.of("minecraft", "fabricloader"), provides, List.of(children), null);
    }
    private static Node embedded(String id, String... depends) {
        return new Node(id, id, true, true, true, List.of(depends), List.of(), List.of(), "Embedded; disable its parent");
    }

    @Test void disablingALibraryCascadesTransitivelyToEnabledDependentsOnly() {
        var nodes = List.of(library("fabric-api", true, List.of("fabric")), mod("sodium", true, "minecraft", "java"),
            mod("lambdynlights", true, "fabric-api"), mod("addon", true, "lambdynlights"), mod("already-off", false, "fabric-api"));
        var plan = ModDependencyPlanner.plan(nodes, List.of("fabric-api"), false);
        assertEquals(List.of("lambdynlights", "addon"), plan.alsoDisable());
        assertTrue(plan.blockers().isEmpty());
        assertTrue(plan.needsConfirmation());
        assertFalse(ModDependencyPlanner.plan(nodes, List.of("sodium"), false).needsConfirmation(), "nothing depends on sodium");
    }

    @Test void providesAliasesAndEmbeddedCopiesKeepDependentsSatisfied() {
        // cloth-config is also embedded in modmenu; fabric-api's "fabric" alias is provided by a second mod.
        var nodes = List.of(mod("cloth-config", true), library("modmenu", true, List.of(), embedded("cloth-config")),
            mod("uses-cloth", true, "cloth-config"), library("fabric-api", true, List.of("fabric")),
            library("fabric-lite", true, List.of("fabric")), mod("old-style", true, "fabric"),
            library("big-library", true, List.of(), embedded("fabric-api-base")), mod("needs-base", true, "fabric-api-base"));
        assertTrue(ModDependencyPlanner.plan(nodes, List.of("cloth-config"), false).alsoDisable().isEmpty());
        assertTrue(ModDependencyPlanner.plan(nodes, List.of("fabric-api"), false).alsoDisable().isEmpty());
        assertEquals(List.of("needs-base"), ModDependencyPlanner.plan(nodes, List.of("big-library"), false).alsoDisable(),
            "removing a parent removes the jars embedded in it");
    }

    @Test void onlyDependenciesBrokenByThisChangeCascade() {
        var nodes = List.of(mod("x", true), mod("broken", true, "x", "missing-lib"), mod("fine", true, "missing-lib-2"));
        var plan = ModDependencyPlanner.plan(nodes, List.of("x"), false);
        assertEquals(List.of("broken"), plan.alsoDisable(), "a newly unsatisfied dependency still cascades");
        assertTrue(ModDependencyPlanner.plan(nodes, List.of("fine"), false).alsoDisable().isEmpty());
        var unrelated = ModDependencyPlanner.plan(List.of(mod("a", true), mod("already-broken", true, "gone")), List.of("a"), false);
        assertTrue(unrelated.alsoDisable().isEmpty(), "an already unsatisfied mod is not disabled by an unrelated change");
    }

    @Test void enablingResolvesRequestedDisabledDependenciesTransitively() {
        var nodes = List.of(mod("addon", false, "lambdynlights"), mod("lambdynlights", false, "fabric-api", "minecraft"),
            library("fabric-api", false, List.of()), mod("sodium", true));
        var plan = ModDependencyPlanner.plan(nodes, List.of("addon"), true);
        assertEquals(List.of("lambdynlights", "fabric-api"), plan.alsoEnable());
        assertTrue(plan.blockers().isEmpty());
    }

    @Test void enablingThroughAnEmbeddedProviderEnablesItsParent() {
        var nodes = List.of(library("modmenu", false, List.of(), embedded("cloth-config")), mod("uses-cloth", false, "cloth-config"));
        assertEquals(List.of("modmenu"), ModDependencyPlanner.plan(nodes, List.of("uses-cloth"), true).alsoEnable());
    }

    @Test void missingRequiredDependencyBlocksAndPlatformIdsNeverDo() {
        var plan = ModDependencyPlanner.plan(List.of(mod("needs-ghost", false, "ghost", "minecraft", "java", "fabricloader", "mixinextras")),
            List.of("needs-ghost"), true);
        assertEquals(1, plan.blockers().size());
        assertTrue(plan.blockers().getFirst().contains("ghost"));
        assertTrue(plan.alsoEnable().isEmpty());
    }

    @Test void pendingDownloadsSatisfyDependenciesButUnknownMetadataWarns() {
        var pending = new Node("iris", "Iris", true, true, false, List.of(), List.of(), List.of(), null);
        var notDownloaded = new Node("offlib", "offlib", false, true, false, List.of(), List.of(), List.of(), null);
        var unavailable = new Node("appleskin", "AppleSkin", false, false, true, List.of(), List.of(), List.of(), null);
        var nodes = List.of(pending, notDownloaded, unavailable, mod("iris-addon", false, "iris"), mod("wants", false, "offlib"),
            mod("sodium", true), mod("needs-appleskin", false, "appleskin"));
        var plan = ModDependencyPlanner.plan(nodes, List.of("iris-addon"), true);
        assertTrue(plan.blockers().isEmpty(), "a pending download is fine: it is downloaded at next launch");
        assertTrue(plan.alsoEnable().isEmpty());
        assertTrue(plan.warnings().isEmpty(), "like the launcher, an enable only checks the mods it switches on");
        var wants = ModDependencyPlanner.plan(nodes, List.of("wants"), true);
        assertEquals(List.of("offlib"), wants.alsoEnable(), "a not-downloaded dependency comes along");
        assertEquals(List.of("1 mod is not downloaded yet: their dependencies are checked at next launch."), wants.warnings());
        assertEquals(List.of("1 mod is not downloaded yet: their dependencies are checked at next launch."),
            ModDependencyPlanner.plan(nodes, List.of("sodium"), false).warnings(), "a disable checks every mod that stays on");
        var blocked = ModDependencyPlanner.plan(nodes, List.of("needs-appleskin"), true);
        assertFalse(blocked.blockers().isEmpty(), "an entry unavailable for this version cannot satisfy a dependency");
        assertFalse(ModDependencyPlanner.plan(nodes, List.of("appleskin"), true).blockers().isEmpty());
    }

    @Test void disablingCoreOrWhatCoreNeedsWarnsThatLadsMenusDisappear() {
        var nodes = List.of(library("fabric-api", true, List.of()), mod(ModDependencyPlanner.CORE_ID, true, "fabric-api", "minecraft"));
        var direct = ModDependencyPlanner.plan(nodes, List.of(ModDependencyPlanner.CORE_ID), false);
        assertEquals(List.of(ModDependencyPlanner.CORE_WARNING), direct.warnings());
        var cascade = ModDependencyPlanner.plan(nodes, List.of("fabric-api"), false);
        assertEquals(List.of(ModDependencyPlanner.CORE_ID), cascade.alsoDisable());
        assertTrue(cascade.warnings().contains(ModDependencyPlanner.CORE_WARNING));
        assertTrue(ModDependencyPlanner.plan(nodes, List.of(ModDependencyPlanner.CORE_ID), true).warnings().isEmpty());
    }

    @Test void nonToggleableAndUnknownTargetsAreBlockers() {
        var platform = new Node("minecraft", "Minecraft", true, true, true, List.of(), List.of(), List.of(), "Platform component");
        var plan = ModDependencyPlanner.plan(List.of(platform), List.of("minecraft", "nope"), false);
        assertEquals(2, plan.blockers().size());
        assertTrue(plan.blockers().getFirst().contains("Platform component"));
    }

    @Test void combinedPlanForSeveralTargets() {
        var nodes = List.of(library("a", true, List.of()), library("b", true, List.of()), mod("needs-a", true, "a"), mod("needs-b", true, "b"));
        var plan = ModDependencyPlanner.plan(nodes, List.of("a", "b", "a"), false);
        assertEquals(List.of("a", "b"), plan.targetIds());
        assertEquals(List.of("needs-a", "needs-b"), plan.alsoDisable());
    }
}
