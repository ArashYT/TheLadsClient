package com.thelads.core.v26_2.mixin;

import com.google.common.collect.Lists;
import com.thelads.core.client.pack.PackAssetMask;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Computes the asset-domain diff mask between the previously selected packs and the newly
 * selected packs when committing the resource pack screen in 26.3 so unaffected reload listeners
 * (SoundManager, ShaderManager) can skip redundant work.
 */
@Mixin(PackSelectionModel.class)
public abstract class PackSelectionModelMixin26 {
    @Shadow @Final private PackRepository repository;
    @Shadow @Final private List<Pack> selected;

    @Inject(method = "commit", at = @At("HEAD"))
    private void ladsBeforeCommit(CallbackInfo ci) {
        if (this.repository == null || this.selected == null) {
            return;
        }
        Collection<Pack> oldPacks = this.repository.getSelectedPacks();
        List<String> oldIds = new ArrayList<>(oldPacks.size());
        for (Pack pack : oldPacks) {
            oldIds.add(pack.getId());
        }
        List<Pack> reversedNew = Lists.reverse(this.selected);
        List<String> newIds = new ArrayList<>(reversedNew.size());
        for (Pack pack : reversedNew) {
            newIds.add(pack.getId());
        }

        Minecraft mc = Minecraft.getInstance();
        Path packDir = mc != null ? mc.getResourcePackDirectory() : null;

        int diffMask = PackAssetMask.computeDiffMask(oldIds, newIds, id -> resolvePackMask(packDir, id));
        int newSelectedMask = 0;
        for (String id : newIds) {
            if (id != null && id.startsWith("file/")) {
                newSelectedMask |= resolvePackMask(packDir, id);
            }
        }
        PackAssetMask.beginPackSwitchReload(diffMask, newSelectedMask);
    }

    @Inject(method = "commit", at = @At("RETURN"))
    private void ladsAfterCommit(CallbackInfo ci) {
        PackAssetMask.clearPendingIfNotStarted();
    }

    private static int resolvePackMask(Path packDir, String id) {
        if (packDir != null && id != null && id.startsWith("file/")) {
            return PackAssetMask.getMask(packDir.resolve(id.substring(5)));
        }
        return PackAssetMask.ALL;
    }
}
