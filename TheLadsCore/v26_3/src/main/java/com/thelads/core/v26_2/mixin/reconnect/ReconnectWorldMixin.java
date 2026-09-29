// SPDX-License-Identifier: LGPL-3.0-only
// API hook adapted from AutoReconnect, Copyright 2023 Bstn1802, 2026 TerminalMC.
package com.thelads.core.v26_2.mixin.reconnect;

import com.thelads.core.v26_2.feature.NativeReconnect;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.server.WorldStem;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class ReconnectWorldMixin {
    @Inject(method = "doWorldLoad", at = @At("HEAD"))
    private void lads$captureWorld(LevelStorageSource.LevelStorageAccess access, PackRepository packs, WorldStem stem,
                                  Optional<GameRules> rules, boolean newWorld, CallbackInfo ci) {
        NativeReconnect.world(access.getLevelId());
    }
}
