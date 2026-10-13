package com.thelads.core.v1_20_1.mixin;

import com.thelads.core.client.pack.PackFolderHelper;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.FolderRepositorySource;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FolderRepositorySource.class)
public abstract class FolderRepositorySourceMixin {
    @Shadow
    public static Pack.ResourcesSupplier detectPackResources(Path path, boolean isSymlink) {
        throw new AssertionError();
    }

    @Inject(method = "discoverPacks(Ljava/nio/file/Path;ZLjava/util/function/BiConsumer;)V", at = @At("HEAD"), cancellable = true)
    private static void ladsDiscoverPacksRecursive(Path folder, boolean isSymlink, BiConsumer<Path, Pack.ResourcesSupplier> consumer, CallbackInfo ci) {
        List<Path> allPacks = PackFolderHelper.scanAllPacks(folder);
        for (Path packPath : allPacks) {
            Pack.ResourcesSupplier supplier = detectPackResources(packPath, isSymlink);
            if (supplier != null) {
                consumer.accept(packPath, supplier);
            }
        }
        ci.cancel();
    }

    @Inject(method = "nameFromPath", at = @At("HEAD"), cancellable = true)
    private static void ladsNameFromPath(Path path, CallbackInfoReturnable<String> cir) {
        String rel = PackFolderHelper.getRelativePackPath(path);
        if (!rel.isEmpty() && !rel.equals(path.getFileName().toString())) {
            cir.setReturnValue(rel);
        }
    }
}
