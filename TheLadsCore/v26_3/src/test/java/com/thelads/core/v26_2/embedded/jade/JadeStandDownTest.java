package com.thelads.core.v26_2.embedded.jade;

/**
 * Unit verification for Jade stand-down resolution logic.
 *
 * <p>Stand-down architecture notes:
 * <ul>
 *   <li>Fabric Loader prioritizes root jars in {@code mods/} over nested jars declaring the same mod ID ("jade").</li>
 *   <li>When Core runs standalone, "jade" is loaded as a nested child container with containingMod "theladscore",
 *       so {@link JadeStandDown#external()} returns {@code false} and Core's embedded Jade mixins apply.</li>
 *   <li>When a real Jade jar is installed in {@code mods/}, Fabric Loader replaces the nested identity with the root jar
 *       (which has no containing mod), so {@link JadeStandDown#external()} returns {@code true}.</li>
 *   <li>When external Jade is detected, {@link JadeMixinPlugin#shouldApplyMixin} returns {@code false} for all Core Jade mixins,
 *       yielding entirely to upstream Jade's own mixin configurations and handlers.</li>
 *   <li>If the launcher renames external Jade to {@code .disabled}, Fabric does not discover it and Core's embedded version runs.</li>
 * </ul>
 */
public final class JadeStandDownTest {
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Check failed: " + message);
        }
    }

    public static void main(String[] args) {
        // When jade is not loaded at all, not external
        check(!JadeStandDown.external(false, null), "no jade installed is not external");
        check(!JadeStandDown.external(false, "theladscore"), "no jade even if containing mod is set");

        // When jade is loaded nested in theladscore, not external (Core's own embedded jade)
        check(!JadeStandDown.external(true, "theladscore"), "jade nested in theladscore is Core's own identity");

        // When jade is loaded as a root mod (jar in mods/), external Jade is detected!
        check(JadeStandDown.external(true, null), "jade as root jar in mods/ is external Jade");

        // When jade is loaded nested in another mod, external Jade is detected!
        check(JadeStandDown.external(true, "othermod"), "jade nested in other mod is external Jade");
    }
}
