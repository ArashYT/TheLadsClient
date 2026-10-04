package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.Cheats189;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.world.WorldType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The world's own Allow Cheats as it loaded (Cheats189), before Essential puts its own switch on the world. */
@Mixin(IntegratedServer.class)
public abstract class IntegratedServerMixin {
    @Inject(method = "loadAllWorlds", at = @At("TAIL"), require = 1)
    private void ladsCheatsLoaded(String saveName, String worldName, long seed, WorldType type, String generatorOptions, CallbackInfo ci) {
        Cheats189.loaded((IntegratedServer) (Object) this);
    }
}
