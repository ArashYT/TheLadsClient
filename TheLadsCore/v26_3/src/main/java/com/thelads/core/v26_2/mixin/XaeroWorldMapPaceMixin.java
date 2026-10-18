package com.thelads.core.v26_2.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.9.12 perf round 2: Xaero's World Map processes its map on the render thread at the end of every frame (MapProcessor
 * .onRenderProcess: chunk writing, every loaded region texture checked for uploads): 8 % of the render thread at 1,400 FPS (Legion
 * JFR). Its budgets follow the time since its last run, so above 250 FPS it now runs every 4 ms instead of every frame (at 250 FPS
 * or less nothing changes). The full map screen keeps every frame. -Dlads.xaeroMapPace=false = every frame. Pseudo: no World Map, no-op.
 */
@Pseudo
@Mixin(targets = "xaero.map.MapProcessor", remap = false)
public class XaeroWorldMapPaceMixin {
    @Unique private static final boolean PACE = Boolean.parseBoolean(System.getProperty("lads.xaeroMapPace", "true"));
    @Unique private static long lads$next;

    @Inject(method = "onRenderProcess", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void lads$pace(Minecraft mc, CallbackInfo ci) {
        if (!PACE) return;
        var screen = mc.gui.screen();
        if (screen != null && screen.getClass().getName().startsWith("xaero.map.")) return;
        long now = System.nanoTime();
        if (now - lads$next < 0) { ci.cancel(); return; }
        lads$next = now + 4_000_000L;
    }
}
