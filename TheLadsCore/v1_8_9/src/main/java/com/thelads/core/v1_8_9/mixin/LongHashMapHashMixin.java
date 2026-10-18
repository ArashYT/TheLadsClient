package com.thelads.core.v1_8_9.mixin;

import net.minecraft.util.LongHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * Vanilla 1.8.9's LongHashMap hashes a chunk key (x in the low 32 bits, z in the high) as x ^ z, so the 625 chunks around a player
 * at render distance 12 share 32-92 buckets and every chunk lookup walks a chain of 7-20 entries (1.9.12: 7% of the game thread
 * with Ionium). A full 64-bit mix (murmur3's finalizer) spreads them. Put, get and remove all hash through here, and the class is
 * transformed before its first use. Not applied with OptiFine, which ships its own LongHashMap (EarlyPlugin189).
 */
@Mixin(LongHashMap.class)
public abstract class LongHashMapHashMixin {
    /**
     * @author The Lads
     * @reason x ^ z put whole diagonals of chunks in one bucket
     */
    @Overwrite
    private static int getHashedKey(long key) {
        key ^= key >>> 33;
        key *= 0xff51afd7ed558ccdL;
        key ^= key >>> 33;
        key *= 0xc4ceb9fe1a85ec53L;
        key ^= key >>> 33;
        return (int) key;
    }
}
