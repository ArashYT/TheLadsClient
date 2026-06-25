package com.thelads.core.mixin;

import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Mixin(PackRepository.class)
public class PackRepositoryMixin {

    @Inject(method = "rebuildSelected", at = @At("RETURN"), cancellable = true)
    private void onRebuildSelected(Collection<String> collections, CallbackInfoReturnable<List<Pack>> cir) {
        try {
            List<Pack> selectedList = cir.getReturnValue();
            System.out.println("[LadsCore] rebuildSelected called. Collections size: " + (collections != null ? collections.size() : "null") + ", Selected list size: " + (selectedList != null ? selectedList.size() : "null"));
            
            if (selectedList == null) {
                return;
            }
            
            List<Pack> vanillaList = new ArrayList<>();
            List<Pack> hiddenList = new ArrayList<>();
            List<Pack> customList = new ArrayList<>();

            for (Pack pack : selectedList) {
                if (pack == null) {
                    System.out.println("[LadsCore] Warning: Null pack encountered in selectedList!");
                    continue;
                }
                String id = pack.getId();
                if (id == null) {
                    System.out.println("[LadsCore] Warning: Pack with null ID encountered!");
                    continue;
                }
                
                boolean isHidden = false;
                try {
                    if (fuzs.resourcepackoverrides.common.services.ClientAbstractions.INSTANCE.isPackHidden(pack)) {
                        isHidden = true;
                    }
                } catch (Throwable t) {
                    try {
                        fuzs.resourcepackoverrides.common.server.packs.PackSelectionOverride override = 
                            fuzs.resourcepackoverrides.common.config.ResourceOverridesManager.getOverride(id);
                        if (override != null && (Boolean.TRUE.equals(override.hidden()) || Boolean.TRUE.equals(override.required()))) {
                            isHidden = true;
                        }
                    } catch (Throwable t2) {}
                }

                if (id.equals("vanilla")) {
                    vanillaList.add(pack);
                } else if (isHidden) {
                    hiddenList.add(pack);
                } else {
                    customList.add(pack);
                }
            }

            List<Pack> newList = new ArrayList<>();
            newList.addAll(vanillaList);
            newList.addAll(hiddenList);
            newList.addAll(customList);
            
            System.out.println("[LadsCore] Rebuilt list: vanilla=" + vanillaList.size() + ", hidden=" + hiddenList.size() + ", custom=" + customList.size() + ", total=" + newList.size());
            cir.setReturnValue(newList);
        } catch (Throwable t) {
            System.err.println("[LadsCore] Exception in PackRepositoryMixin.onRebuildSelected:");
            t.printStackTrace();
        }
    }
}
