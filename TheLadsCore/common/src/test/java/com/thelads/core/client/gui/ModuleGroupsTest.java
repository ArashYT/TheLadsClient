package com.thelads.core.client.gui;

import static org.junit.jupiter.api.Assertions.*;

import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ModuleGroupsTest {
    @Test void everyModuleHasAGroupAndEveryGroupedNameIsAModule() {
        Set<String> registered = new HashSet<>();
        for (Module module : ModuleManager.getInstance().getModules()) {
            registered.add(module.getName());
            assertNotNull(ModuleGroups.groupOf(module.getName()), module.getName() + " would land in Other");
        }
        for (ModuleGroups.Group group : ModuleGroups.GROUPS) {
            assertTrue(ModuleGroups.CATEGORIES.contains(group.category()), group.category());
            for (String member : group.members()) if (!member.equals("UnfocusedFpsCap")) assertTrue(registered.contains(member), member + " is no module");
        }
    }
    @Test void noModuleSitsInTwoGroups() {
        Set<String> seen = new HashSet<>();
        for (ModuleGroups.Group group : ModuleGroups.GROUPS) for (String member : group.members()) assertTrue(seen.add(member), member);
    }
    @Test void aBundleStaysInOneGroupSoItsRowHasOneHome() {
        for (ModuleGroups.Bundle bundle : ModuleGroups.BUNDLES) {
            ModuleGroups.Group first = ModuleGroups.groupOf(bundle.members().get(0));
            for (String member : bundle.members()) assertSame(first, ModuleGroups.groupOf(member), member);
        }
    }
    @Test void everyCategoryHasAnIcon() {
        for (String category : ModuleGroups.CATEGORIES) assertNotNull(ModuleGroups.icon(category), category);
    }
    @Test void aFoldRemembersOnlyWhatDiffersFromItsDefault() {
        try {
            assertFalse(ModuleGroups.isOpen("a", false));
            assertTrue(ModuleGroups.isOpen("a", true));
            ModuleGroups.toggle("a");
            assertTrue(ModuleGroups.isOpen("a", false));
            assertEquals(Set.of("a"), ModuleGroups.flipped());
            ModuleGroups.toggle("a");
            assertTrue(ModuleGroups.flipped().isEmpty());
        } finally { ModuleGroups.setFlipped(java.util.List.of()); }
    }
}
