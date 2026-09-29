package com.thelads.core.v26_2.feature.tabtweaks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.v26_2.feature.tabtweaks.TabProbeAccess;
import java.util.Collection;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerTabOverlay.class)
public abstract class TabProbeMixin implements TabProbeAccess {
    @Unique private List<PlayerInfo> ladsTab$players;
    @Shadow private List<PlayerInfo> getPlayerInfos() { throw new AssertionError(); }
    @Shadow protected abstract void extractPingIcon(GuiGraphicsExtractor graphics, int width, int x, int y, PlayerInfo player);
    @Override public void ladsTab$fixture(List<PlayerInfo> players) {
        if (!Boolean.getBoolean("thelads.verifyIntegrations")) throw new IllegalStateException("Runtime probe is disabled");
        ladsTab$players = List.copyOf(players);
    }
    @Override public List<PlayerInfo> ladsTab$players() { return getPlayerInfos(); }
    @Override public void ladsTab$ping(GuiGraphicsExtractor graphics, PlayerInfo player) { extractPingIcon(graphics, 100, 10, 20, player); }
    @WrapOperation(method = "getPlayerInfos", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;getListedOnlinePlayers()Ljava/util/Collection;"))
    private Collection<PlayerInfo> localFixture(ClientPacketListener listener, Operation<Collection<PlayerInfo>> original) {
        return ladsTab$players == null ? original.call(listener) : ladsTab$players;
    }
}
