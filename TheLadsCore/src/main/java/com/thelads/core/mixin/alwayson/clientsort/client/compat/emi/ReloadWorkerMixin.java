/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Pseudo
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.thelads.core.mixin.alwayson.clientsort.client.compat.emi;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import com.thelads.core.features.alwayson.clientsort.ClientSortClient;
import com.thelads.core.features.alwayson.clientsort.order.CreativeSearchOrder;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets={"dev.emi.emi.runtime.EmiReloadManager$ReloadWorker"}, remap=false)
public abstract class ReloadWorkerMixin {
    @Inject(method={"run"}, at={@At(value="HEAD")})
    private void beforeRun(CallbackInfo ci) {
        ClientSortClient.updateBlockedByEmi = false;
        ClientSortClient.emiReloading = true;
    }

    @Inject(method={"run"}, at={@At(value="RETURN")})
    private void afterRun(CallbackInfo ci) {
        if (ClientSortClient.updateBlockedByEmi) {
            Minecraft.getInstance().execute(() -> {
                ClientSortClient.LOG.info("EMI reload finished; updating search order", new Object[0]);
                CreativeSearchOrder.tryRefreshStackPositionMap();
            });
        }
        ClientSortClient.emiReloading = false;
    }
}
