/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.server.MinecraftServer
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.thelads.core.mixin.alwayson.letmedespawn;

import com.thelads.core.features.alwayson.letmedespawn.LetMeDespawn;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={MinecraftServer.class})
public class RegistryCacheClearerMixin {
    @Inject(method={"stopServer()V"}, at={@At(value="HEAD")})
    private void letmedespawn$onServerStop(CallbackInfo ci) {
        LetMeDespawn.clearRegistryCache();
    }

    @Inject(method={"loadLevel()V"}, at={@At(value="HEAD")})
    private void letmedespawn$onWorldLoad(CallbackInfo ci) {
        LetMeDespawn.clearRegistryCache();
    }
}

