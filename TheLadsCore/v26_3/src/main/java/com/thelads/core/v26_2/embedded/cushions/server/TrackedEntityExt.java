// Derived from Optimized Cushions 1.0.0 (tag 1.0.0) by NikitaCartes (MIT); see META-INF/lads-sources/cushions/LICENSE.
package com.thelads.core.v26_2.embedded.cushions.server;

import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.Entity;

/** Duck interface on {@code ChunkMap$TrackedEntity} (private inner class). */
public interface TrackedEntityExt {
    Entity optimizedcushions$entity();

    ServerEntity optimizedcushions$serverEntity();
}
