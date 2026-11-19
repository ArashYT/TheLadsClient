package com.thelads.core.client.ladsapi;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The "Sodium" rule "Animate Only Visible Textures: false". Sodium reads its saved option straight from a field, so instead of
 * changing it the atlas tick tells Sodium every animated sprite was drawn last tick ({@code sodium$setActive}) and Sodium then
 * skips none. A sprite whose frame changed while Sodium skipped it keeps the old pixels until its next frame change, so when the
 * rule turns on {@link #epoch} moves and each atlas / ticker redraws its current frame once. Render thread only.
 */
public final class SodiumAnimation {
    /** Moves each time the rule turns on; a ticker that last redrew in an older epoch redraws its current frame. */
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore/SodiumAnimation");
    public static volatile int epoch;
    private static boolean all;
    private static Class<?> handleClass;
    private static MethodHandle setActive;

    private SodiumAnimation() {}

    /** Once per atlas tick: whether the rule is on, and starts a new epoch on the first tick it is. */
    public static boolean on() {
        boolean now = ServerSettings.sodiumAnimatesAll();
        if (now != all) LOGGER.info("Lads server rule: Sodium animate-only-visible-textures {} (Sodium's saved option is untouched)", now ? "overridden OFF, all animated textures redrawn" : "back to the player's own value");
        if (now && !all) epoch++;
        all = now;
        return now;
    }

    /** Sodium's own flag on each SpriteContents (its animation mixin reads then clears it every tick). */
    public static void markActive(List<?> contents) {
        for (Object c : contents) {
            try {
                if (c.getClass() != handleClass) {
                    setActive = MethodHandles.publicLookup().findVirtual(c.getClass(), "sodium$setActive", MethodType.methodType(void.class, boolean.class))
                        .asType(MethodType.methodType(void.class, Object.class, boolean.class));
                    handleClass = c.getClass();
                }
                setActive.invokeExact(c, true);
            } catch (Throwable absent) {
                return; // no Sodium animation tracking in this game: nothing is skipped
            }
        }
    }
}
