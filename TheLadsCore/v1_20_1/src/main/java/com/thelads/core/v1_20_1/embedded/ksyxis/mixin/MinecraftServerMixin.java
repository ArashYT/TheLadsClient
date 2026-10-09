/*
 * Ksyxis is a third-party mod for Minecraft Java Edition that
 * speed ups your world loading by removing unneeded chunks.
 *
 * MIT License
 *
 * Copyright (c) 2021-2026 VidTu
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 * SPDX-License-Identifier: MIT
 */
// Modified by The Lads: repackaged into Lads Core; ported to Minecraft 1.20.1 from the Lads 26.3 embedded copy. 26.3 keeps the
// injections for the 1.21.9+ player-spawn preparation; 1.20.1 prepares the world's start region inside prepareLevels instead.
package com.thelads.core.v1_20_1.embedded.ksyxis.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.v1_20_1.embedded.ksyxis.KsyxisInit1201;
import com.thelads.core.v1_20_1.embedded.ksyxis.KsyxisLog;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ksyxis {@code MinecraftServerMixin}: logs the world-load speed-up and, on 1.20.1, removes the start region. Vanilla adds a
 * radius-11 {@code START} ticket around the world spawn and holds the "Preparing spawn area" screen until all 21 x 21 chunks are
 * generated and ticking ({@code getTickingGenerated() == 441}). Here no ticket is added and the wait ends at once; the chunks
 * around the player are loaded when the player joins, as they are anywhere else in the world. Consequence (as with Ksyxis itself):
 * the spawn chunks are no longer kept loaded.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Unique private static final Logger KSYXIS_LOGGER = LoggerFactory.getLogger("Ksyxis/MinecraftServerMixin");
    /** The start region was left out in the current prepareLevels run. */
    @Unique private boolean ksyxis$startTicketSkipped;
    @Unique private long ksyxis$prepareStart;

    @Inject(method = "prepareLevels", at = @At("HEAD"))
    private void ksyxis_prepareLevels_head(ChunkProgressListener listener, CallbackInfo ci) {
        KSYXIS_LOGGER.info("Ksyxis: Speeding up the world loading... ({} {})", this.getClass().getName(), ci.getId());
        this.ksyxis$startTicketSkipped = false;
        this.ksyxis$prepareStart = System.nanoTime();
    }

    @Inject(method = "prepareLevels", at = @At("RETURN"))
    private void ksyxis_prepareLevels_return(ChunkProgressListener listener, CallbackInfo ci) {
        KsyxisInit1201.prepared(this.ksyxis$startTicketSkipped, System.nanoTime() - this.ksyxis$prepareStart);
    }

    // require = 0 on both wraps: if another loader's patched prepareLevels no longer has exactly these two calls, the world
    // simply loads the vanilla way (the probe then reports the speed-up as not applied) instead of the game failing to start.
    @WrapOperation(method = "prepareLevels", require = 0, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerChunkCache;addRegionTicket(Lnet/minecraft/server/level/TicketType;Lnet/minecraft/world/level/ChunkPos;ILjava/lang/Object;)V"))
    private void ksyxis_prepareLevels_addTicket(ServerChunkCache cache, TicketType<?> type, ChunkPos pos, int radius, Object key, Operation<Void> original) {
        if (type != TicketType.START) {
            original.call(cache, type, pos, radius, key);
            return;
        }
        this.ksyxis$startTicketSkipped = true;
        KsyxisLog.once("start region ticket radius " + radius + " -> none");
    }

    @WrapOperation(method = "prepareLevels", require = 0, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerChunkCache;getTickingGenerated()I"))
    private int ksyxis_prepareLevels_tickingGenerated(ServerChunkCache cache, Operation<Integer> original) {
        // Vanilla waits for 441 ticking chunks (the 21 x 21 start region); without its ticket none are coming.
        return this.ksyxis$startTicketSkipped ? 441 : original.call(cache);
    }
}
