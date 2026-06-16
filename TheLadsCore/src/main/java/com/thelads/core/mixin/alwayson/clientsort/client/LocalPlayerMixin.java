/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.player.LocalPlayer
 *  net.minecraft.server.permissions.PermissionSet
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.thelads.core.mixin.alwayson.clientsort.client;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import com.thelads.core.features.alwayson.clientsort.ClientSortClient;
import com.thelads.core.features.alwayson.clientsort.interaction.InteractionManager;
import com.thelads.core.features.alwayson.clientsort.order.CreativeSearchOrder;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.permissions.PermissionSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={LocalPlayer.class})
public abstract class LocalPlayerMixin {
    @Inject(method={"clientSideCloseContainer"}, at={@At(value="HEAD")})
    public void beforeContainerClose(CallbackInfo callbackInfo) {
        InteractionManager.clear();
        ClientSortClient.operatingClient = false;
        ClientSortClient.clientOpQueue.clear();
    }

    @Inject(method={"setPermissions"}, at={@At(value="RETURN")})
    public void afterPermissionLevelChange(PermissionSet permissionSet, CallbackInfo ci) {
        if (!ClientSortClient.searchOrderUpdated) {
            ClientSortClient.searchOrderUpdated = true;
            CreativeSearchOrder.tryRefreshStackPositionMap();
        }
    }
}
