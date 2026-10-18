package com.thelads.smp.features.arise.nb2;

/**
 * Ensures the singleplayer menu always opens to the world selection list
 * rather than automatically launching the Create World wizard when no saves exist.
 */
public final class OWorld2CreateManager {
    private OWorld2CreateManager() {}

    public static boolean isBypassEnabled() {
        return NB2Config.get().oWorld2Create;
    }
}
