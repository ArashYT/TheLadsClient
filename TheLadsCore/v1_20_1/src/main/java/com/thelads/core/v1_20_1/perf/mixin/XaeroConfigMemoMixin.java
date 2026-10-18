package com.thelads.core.v1_20_1.perf.mixin;

import java.util.IdentityHashMap;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.9.12 perf round 2: Xaero's Minimap and World Map read their settings through ClientConfigManager.getEffective(option) hundreds
 * of times a frame, each resolving the profile, redirectors and server enforcement (~3 % of the render thread in flight, Legion JFR).
 * On the render thread an answer is reused for 1 ms: a changed setting shows a millisecond later. -Dlads.xaeroConfigMemo=false = off.
 * Pseudo: no Xaero, no-op.
 */
@Pseudo
@Mixin(targets = "xaero.lib.client.config.ClientConfigManager", remap = false)
public class XaeroConfigMemoMixin {
    @Unique private static final boolean MEMO = Boolean.parseBoolean(System.getProperty("lads.xaeroConfigMemo", "true"));
    @Unique private static final long TTL = 1_000_000L;
    @Unique private static final class Stamp { Object value; long at; }
    @Unique private final IdentityHashMap<Object, Stamp> lads$memo = new IdentityHashMap<>();

    @Inject(method = "getEffective(Lxaero/lib/common/config/option/ConfigOption;)Ljava/lang/Object;", at = @At("HEAD"), cancellable = true,
        remap = false, require = 0)
    private void lads$reuse(@Coerce Object option, CallbackInfoReturnable<Object> cir) {
        if (!MEMO || !Minecraft.getInstance().isSameThread()) return;
        Stamp stamp = lads$memo.get(option);
        if (stamp != null && System.nanoTime() - stamp.at < TTL) cir.setReturnValue(stamp.value);
    }

    @Inject(method = "getEffective(Lxaero/lib/common/config/option/ConfigOption;)Ljava/lang/Object;", at = @At("RETURN"), remap = false, require = 0)
    private void lads$keep(@Coerce Object option, CallbackInfoReturnable<Object> cir) {
        if (!MEMO || !Minecraft.getInstance().isSameThread()) return;
        Stamp stamp = lads$memo.computeIfAbsent(option, key -> new Stamp());
        stamp.value = cir.getReturnValue();
        stamp.at = System.nanoTime();
    }
}
