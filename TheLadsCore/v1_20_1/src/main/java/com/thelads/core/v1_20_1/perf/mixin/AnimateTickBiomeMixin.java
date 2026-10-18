package com.thelads.core.v1_20_1.perf.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.9.12 perf round 2: every client tick vanilla picks 1,334 random positions up to 32 blocks around the player and, for each one
 * not in a full block, looks up its biome only to see whether that biome spawns ambient particles (vanilla: the nether biomes):
 * ~9 % of the 1.20.1 render thread (Legion JFR). Once a second the biome palettes of the chunks in that range are checked; while none
 * of them has ambient particles, the lookup answers with one of their biomes (same result: no particle). -Dlads.animateBiomes=false = off.
 */
@Mixin(ClientLevel.class)
public abstract class AnimateTickBiomeMixin {
    @Unique private static final boolean SKIP = Boolean.parseBoolean(System.getProperty("lads.animateBiomes", "true"));
    @Unique private long lads$checkedAt;
    /** A biome of the area without ambient particles, while no biome in range has them; null: look up as usual. */
    @Unique private Holder<Biome> lads$quiet;

    @Inject(method = "animateTick", at = @At("HEAD"), require = 0)
    private void lads$checkArea(int x, int y, int z, CallbackInfo ci) {
        if (!SKIP) return;
        long now = System.nanoTime();
        if (lads$checkedAt != 0 && now - lads$checkedAt < 1_000_000_000L) return;
        lads$checkedAt = now;
        Holder<Biome> quiet = null;
        ClientLevel level = (ClientLevel) (Object) this;
        for (int cx = (x >> 4) - 2; cx <= (x >> 4) + 2; cx++) {
            for (int cz = (z >> 4) - 2; cz <= (z >> 4) + 2; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null) continue;
                for (LevelChunkSection section : chunk.getSections()) {
                    var biomes = section.getBiomes();
                    if (biomes.maybeHas(biome -> biome.value().getAmbientParticle().isPresent())) { lads$quiet = null; return; }
                    if (quiet == null) quiet = biomes.get(0, 0, 0);
                }
            }
        }
        lads$quiet = quiet;
    }

    @WrapOperation(method = "doAnimateTick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/multiplayer/ClientLevel;getBiome(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Holder;"), require = 0)
    private Holder<Biome> lads$biome(ClientLevel level, BlockPos pos, Operation<Holder<Biome>> original) {
        Holder<Biome> quiet = lads$quiet;
        return quiet != null ? quiet : original.call(level, pos);
    }
}
