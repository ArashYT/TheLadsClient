package com.thelads.core.v1_21_11.mixin.chrome;

import com.thelads.core.client.pack.LargePackWarning;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Toast when the resource pack screen switches on a large pack (every reload is slower with it); reads only the zip's size. */
@Mixin(PackSelectionModel.class)
public abstract class PackSelectionModelWarnMixin12111 {
    @Shadow @Final private PackRepository repository;
    @Shadow @Final private List<Pack> selected;

    @Inject(method = "commit", at = @At("HEAD"))
    private void ladsWarnLargePacks(CallbackInfo ci) {
        if (this.repository == null || this.selected == null) return;
        Minecraft mc = Minecraft.getInstance();
        Collection<Pack> oldPacks = this.repository.getSelectedPacks();
        List<String> oldIds = new ArrayList<>(oldPacks.size());
        for (Pack pack : oldPacks) oldIds.add(pack.getId());
        List<String> newIds = new ArrayList<>();
        for (Pack pack : Lists.reverse(this.selected)) newIds.add(pack.getId());
        String large = LargePackWarning.forAddedPacks(oldIds, newIds, mc.getResourcePackDirectory());
        if (large != null) SystemToast.add(mc.getToastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION, Component.literal("Large resource pack"), Component.literal(large));
    }
}
