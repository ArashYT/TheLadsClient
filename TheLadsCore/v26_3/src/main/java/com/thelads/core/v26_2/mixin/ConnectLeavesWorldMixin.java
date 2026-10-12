package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.gui.PauseMultiplayer;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** A join from the pause menu's server list leaves the open world or server first (PauseMultiplayer.leaveWorld). */
@Mixin(ConnectScreen.class)
abstract class ConnectLeavesWorldMixin {
    @ModifyVariable(method = "startConnecting", at = @At("HEAD"), argsOnly = true, require = 1)
    private static Screen lads$leaveWorldFirst(Screen parent) {
        return PauseMultiplayer.leaveWorld(parent);
    }
}
