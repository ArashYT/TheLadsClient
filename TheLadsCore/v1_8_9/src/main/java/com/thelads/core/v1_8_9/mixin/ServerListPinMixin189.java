package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.client.LadsNetwork;
import com.thelads.core.client.ServerListDrag;
import com.thelads.core.shared.ServerListMerge;
import java.util.List;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The Lads Network kept first (not in a modpack: LadsNetwork.isPinned), so a hand-edited or re-saved servers.dat heals itself; no swap moves it. */
@Mixin(ServerList.class)
abstract class ServerListPinMixin189 {
    @Shadow @Final private List<ServerData> servers;

    @Inject(method = "loadServerList", at = @At("RETURN"))
    private void ladsPinLoaded(CallbackInfo ci) {
        ladsPin();
    }

    @Inject(method = "saveServerList", at = @At("HEAD"))
    private void ladsPinSaved(CallbackInfo ci) {
        ladsPin();
    }

    /** As the launcher's pin: first, the network's own name, server packs on unless the player chose. */
    @Unique
    private void ladsPin() {
        if (!LadsNetwork.titleButton()) return;
        ServerListMerge.pinFirst(servers, data -> LadsNetwork.isPinned(data.serverIP), () -> new ServerData(LadsNetwork.NAME, LadsNetwork.ADDRESS, false));
        ServerData pinned = servers.get(0);
        pinned.serverName = LadsNetwork.NAME;
        pinned.serverIP = LadsNetwork.ADDRESS;
    }

    @Inject(method = "swapServers", at = @At("HEAD"), cancellable = true)
    private void ladsKeepPinned(int from, int to, CallbackInfo ci) {
        if (!servers.isEmpty() && !ServerListDrag.canSwap(from, to, ServerListDrag.firstMovable(servers.get(0).serverIP))) ci.cancel();
    }
}
