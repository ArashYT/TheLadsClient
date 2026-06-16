/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.server.level.ChunkMap
 *  org.spongepowered.asm.mixin.Mixin
 */
package com.thelads.core.mixin.alwayson.vmp.playerwatching.optimize_nearby_player_lookups;

import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value={ChunkMap.class})
public abstract class MixinThreadedAnvilChunkStorage {
}

