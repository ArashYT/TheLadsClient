/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.network.Connection
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.server.network.CommonListenerCookie
 *  net.minecraft.server.players.PlayerList
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package squeek.appleskin.mixin;

import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import squeek.appleskin.network.SyncHandler;

@Mixin(value={PlayerList.class})
public class PlayerListMixin {
    @Inject(at={@At(value="TAIL")}, method={"placeNewPlayer"})
    private void onPlayerConnect(Connection conn, ServerPlayer player, CommonListenerCookie clientData, CallbackInfo info) {
        SyncHandler.onPlayerLoggedIn(player);
    }
}

