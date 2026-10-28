package com.thelads.core.v26_2.embedded.jade;

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
