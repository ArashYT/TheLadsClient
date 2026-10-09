package com.thelads.core.v1_20_1.game.mixin;

import com.thelads.core.client.WorldCheats;
import com.thelads.core.v1_20_1.game.cheats.Cheats1201;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Cheats stay as the host set them (WorldCheats, Cheats1201): Open to LAN's Allow Cheats is the host's choice for joined players. */
@Mixin(IntegratedServer.class)
public abstract class CheatsMixin {
    @Inject(method = "publishServer", at = @At("RETURN"))
    private void lads$keepGuests(GameType gameType, boolean allowCheats, int port, CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValueZ()) WorldCheats.guests(Cheats1201.folder((IntegratedServer) (Object) this), allowCheats);
    }
}
