package com.thelads.core.v26_2.mixin;

import com.thelads.core.client.pack.PackFolderHelper;
import java.nio.file.Path;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.FolderRepositorySource;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.world.level.validation.DirectoryValidator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FolderRepositorySource.class)
public abstract class FolderRepositorySourceMixin {
    @Shadow
    public static void discoverPacks(Path folder, DirectoryValidator validator, BiConsumer<Path, Pack.ResourcesSupplier> consumer) {
        throw new AssertionError();
    }

    @Inject(method = "discoverPacks", at = @At("TAIL"))
    private static void ladsDiscoverSubfolders(Path folder, DirectoryValidator validator, BiConsumer<Path, Pack.ResourcesSupplier> consumer, CallbackInfo ci) {
        try (var stream = java.nio.file.Files.newDirectoryStream(folder)) {
            for (Path child : stream) {
                if (PackFolderHelper.isFolder(child)) {
                    discoverPacks(child, validator, consumer);
                }
            }
        } catch (Throwable ignored) {}
    }

    @Inject(method = "nameFromPath", at = @At("HEAD"), cancellable = true)
    private static void ladsNameFromPath(Path path, CallbackInfoReturnable<String> cir) {
        String rel = PackFolderHelper.getRelativePackPath(path);
        if (!rel.isEmpty() && !rel.equals(path.getFileName().toString())) {
            cir.setReturnValue(rel);
        }
    }
}
