package com.thelads.core.v1_20_1.perf.mixin;

import org.lwjgl.system.MemoryStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 1.9.12 perf round 2: Sodium 0.5 pushes a MemoryStack frame for every entity cuboid it draws, and each MemoryStack.stackPush() is
 * a ThreadLocal lookup: 18 % of the render thread with 200 mobs on Java 17 (Legion JFR, every sample on the lookup). The thread's
 * stack is kept with the thread it belongs to, so the push is a field read. -Dlads.cuboidStack=false = Sodium's own call.
 * Pseudo: no Sodium 0.5, no-op.
 */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.immediate.model.EntityRenderer", remap = false)
public class SodiumCuboidStackMixin {
    @Unique private static final boolean CACHE = Boolean.parseBoolean(System.getProperty("lads.cuboidStack", "true"));
    @Unique private record Owned(Thread thread, MemoryStack stack) {}
    @Unique private static Owned lads$owned = new Owned(null, null);

    @Redirect(method = "renderCuboid", at = @At(value = "INVOKE", target = "Lorg/lwjgl/system/MemoryStack;stackPush()Lorg/lwjgl/system/MemoryStack;"),
        remap = false, require = 0)
    private static MemoryStack lads$push() {
        if (!CACHE) return MemoryStack.stackPush();
        Owned owned = lads$owned;
        Thread thread = Thread.currentThread();
        if (owned.thread() != thread) lads$owned = owned = new Owned(thread, MemoryStack.stackGet());
        return owned.stack().push();
    }
}
