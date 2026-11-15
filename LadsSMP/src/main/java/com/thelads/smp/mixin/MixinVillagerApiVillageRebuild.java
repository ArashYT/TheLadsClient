package com.thelads.smp.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Villager API regenerates its 96 village files (delete the folder, rewrite every pool) every time the data pack repository reloads,
 * on the render thread: three times per world creation (1.7-6 s each on a slow PC, the window stands still). The files depend only on the
 * villagerpacks folder, so when nothing in it changed since the last rebuild of this session, the previous result is still valid.
 */
@Pseudo
@Mixin(targets = "com.frikinjay.villagerapi.villagerpack.village.VillageGenerator", remap = false)
public class MixinVillagerApiVillageRebuild {
    @Unique private static Map<String, Path> ladssmp$lastResult;
    @Unique private static String ladssmp$lastFingerprint;

    @Unique
    private static String ladssmp$fingerprint() {
        Path packs = FabricLoader.getInstance().getGameDir().resolve("villagerpacks");
        long count = 0, hash = 17;
        if (Files.isDirectory(packs)) {
            try (Stream<Path> s = Files.walk(packs)) {
                for (Path p : (Iterable<Path>) s::iterator) {
                    count++;
                    hash = hash * 31 + p.toString().hashCode();
                    try { hash = hash * 31 + Files.getLastModifiedTime(p).toMillis() + Files.size(p); } catch (IOException ignored) { }
                }
            } catch (IOException | RuntimeException e) {
                return null; // unknown: always rebuild
            }
        }
        return count + ":" + hash + ":" + Files.isDirectory(FabricLoader.getInstance().getGameDir().resolve(".villagerapi/generated"));
    }

    @Inject(method = "rebuild", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void ladssmp$skipUnchangedRebuild(CallbackInfoReturnable<Map<String, Path>> cir) {
        String now = ladssmp$fingerprint();
        if (now != null && ladssmp$lastResult != null && now.equals(ladssmp$lastFingerprint) && now.endsWith(":true")) cir.setReturnValue(ladssmp$lastResult);
    }

    @Inject(method = "rebuild", at = @At("RETURN"), remap = false, require = 0)
    private static void ladssmp$rememberRebuild(CallbackInfoReturnable<Map<String, Path>> cir) {
        ladssmp$lastResult = cir.getReturnValue();
        ladssmp$lastFingerprint = ladssmp$fingerprint();
    }
}
