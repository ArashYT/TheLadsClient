package com.thelads.core.v26_2.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 1.9.12 perf round 2: the frame's "minimized" flag (GameRenderer.extractWindow). Sodium redirects it to also ask SDL for the window
 * flags (its Intel unfocused-blit workaround), every frame; under Ixeris that is a blocking round trip to the main thread: 5 % of the
 * render thread with the HUD, 8 % without (Legion JFR). Vanilla's own flag (set by the minimize event) still answers at once; the
 * extra SDL check runs at most every 50 ms. -Dlads.minimizedPoll=false = every frame.
 */
@Mixin(GameRenderer.class)
public class WindowMinimizedPollMixin {
    @Unique private static final boolean POLL = Boolean.parseBoolean(System.getProperty("lads.minimizedPoll", "true"));
    @Unique private static long lads$next;
    @Unique private static boolean lads$minimized;

    @WrapOperation(method = "extractWindow", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;isIconified()Z"), require = 0)
    private boolean lads$pollMinimized(Window window, Operation<Boolean> original) {
        if (!POLL || window.isIconified()) return original.call(window);
        long now = System.nanoTime();
        if (now - lads$next >= 0) {
            lads$minimized = original.call(window);
            lads$next = now + 50_000_000L;
        }
        return lads$minimized;
    }
}
