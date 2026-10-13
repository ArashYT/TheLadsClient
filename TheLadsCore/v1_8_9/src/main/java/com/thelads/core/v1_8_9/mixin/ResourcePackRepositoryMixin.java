package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.LunarPacks189;
import java.io.File;
import java.util.List;
import net.minecraft.client.resources.ResourcePackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lunar Client's 1.8 resource packs in the pack list (LunarPacks189). */
@Mixin(ResourcePackRepository.class)
public class ResourcePackRepositoryMixin {
    @org.spongepowered.asm.mixin.Shadow private File dirResourcepacks;

    @Inject(method = "getResourcePackFiles", at = @At("RETURN"), cancellable = true, require = 1)
    private void ladsLunarPacks(CallbackInfoReturnable<List<File>> callback) {
        List<File> list = callback.getReturnValue();
        if (dirResourcepacks != null && dirResourcepacks.isDirectory()) {
            List<java.nio.file.Path> allPacks = com.thelads.core.client.pack.PackFolderHelper.scanAllPacks(dirResourcepacks.toPath());
            List<File> result = new java.util.ArrayList<File>(list);
            java.util.Set<String> seen = new java.util.HashSet<String>();
            for (File f : list) seen.add(f.getAbsolutePath());
            for (java.nio.file.Path p : allPacks) {
                File f = p.toFile();
                if (seen.add(f.getAbsolutePath())) {
                    result.add(f);
                }
            }
            callback.setReturnValue(LunarPacks189.withLunar(result));
            return;
        }
        callback.setReturnValue(LunarPacks189.withLunar(list));
    }
}
