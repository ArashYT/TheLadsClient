package com.thelads.core.v26_2.embedded.jade;

import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

/**
 * The Jade source port stands down while the real Jade mod is installed. Core nests a metadata-only jar with the mod id "jade"
 * (its entrypoints start the port), so "jade is loaded" is always true; the real mod is the one that is not nested in Core.
 * Fabric ranks a jar in mods/ above a nested jar with the same id, so the real Jade replaces that identity and none of the
 * port's entrypoints run. What is left to stop is Core's own mixin config for the port ({@link JadeMixinPlugin}).
 */
public final class JadeStandDown {
    private static final String CORE_ID = "theladscore";
    private static final boolean EXTERNAL = detect();

    private JadeStandDown() {}

    /** True when a Jade other than Core's nested identity is installed (a jar in mods/, or nested in another mod). */
    public static boolean external() { return EXTERNAL; }

    /** Version of that Jade, for the log. */
    static String externalVersion() {
        return FabricLoader.getInstance().getModContainer("jade").map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("?");
    }

    private static boolean detect() {
        Optional<ModContainer> jade = FabricLoader.getInstance().getModContainer("jade");
        return external(jade.isPresent(), jade.flatMap(ModContainer::getContainingMod).map(parent -> parent.getMetadata().getId()).orElse(null));
    }

    /** @param containingModId the id of the mod that "jade" is nested in; null for a jar in mods/ */
    static boolean external(boolean jadeLoaded, String containingModId) {
        return jadeLoaded && !CORE_ID.equals(containingModId);
    }
}
