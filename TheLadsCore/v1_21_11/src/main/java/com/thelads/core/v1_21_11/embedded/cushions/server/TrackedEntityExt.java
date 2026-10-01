// Derived from Optimized Cushions Backport 1.0.0 (commit a6de9cc) by NikitaCartes (MIT); see META-INF/lads-sources/cushions/LICENSE.
package com.thelads.core.v1_21_11.embedded.cushions.server;

import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.Entity;

/** Duck interface on {@code ChunkMap$TrackedEntity} (private inner class). */
public interface TrackedEntityExt {
    Entity optimizedcushions$entity();

    ServerEntity optimizedcushions$serverEntity();
}
