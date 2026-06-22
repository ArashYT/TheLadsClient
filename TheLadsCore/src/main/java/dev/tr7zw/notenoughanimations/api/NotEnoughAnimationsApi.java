/*
 * Decompiled with CFR 0.152.
 */
package dev.tr7zw.notenoughanimations.api;

import dev.tr7zw.notenoughanimations.NEAnimationsMod;
import dev.tr7zw.notenoughanimations.api.BasicAnimation;

public class NotEnoughAnimationsApi {
    public static void registerAnimation(BasicAnimation animation) {
        NEAnimationsMod.INSTANCE.animationProvider.addAnimation(animation);
    }

    public static void refreshEnabledAnimations() {
        NEAnimationsMod.INSTANCE.animationProvider.refreshEnabledAnimations();
    }
}

