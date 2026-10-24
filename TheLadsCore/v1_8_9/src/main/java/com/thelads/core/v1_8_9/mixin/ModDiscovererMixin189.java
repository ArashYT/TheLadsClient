package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.fml.AsyncModDiscoverer189;
import java.io.File;
import java.util.List;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.fml.common.discovery.ModCandidate;
import net.minecraftforge.fml.common.discovery.ModDiscoverer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts FML's sequential mod discovery to parallelize jar I/O and ASM bytecode exploration (PolyPatcher parity).
 */
@Mixin(value = ModDiscoverer.class, remap = false)
public abstract class ModDiscovererMixin189 {
    @Shadow private List<ModCandidate> candidates;
    @Shadow private ASMDataTable dataTable;
    @Shadow private List<File> nonModLibs;

    @Inject(method = "identifyMods", at = @At("HEAD"), cancellable = true)
    private void ladsAsyncIdentifyMods(CallbackInfoReturnable<List<ModContainer>> cir) {
        List<ModContainer> mods = AsyncModDiscoverer189.identifyMods(candidates, dataTable, nonModLibs);
        cir.setReturnValue(mods);
    }
}
