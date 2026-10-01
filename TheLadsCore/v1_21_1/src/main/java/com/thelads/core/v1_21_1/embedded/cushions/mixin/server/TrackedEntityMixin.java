// Derived from Optimized Cushions Backport 1.0.0 (commit a6de9cc) by NikitaCartes (MIT); see META-INF/lads-sources/cushions/LICENSE.
package com.thelads.core.v1_21_1.embedded.cushions.mixin.server;

import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import com.thelads.core.v1_21_1.embedded.cushions.server.TrackedEntityExt;

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class TrackedEntityMixin implements TrackedEntityExt {
    @Override
    public Entity optimizedcushions$entity() {
        return ((TrackedEntityAccessor) this).optimizedcushions$accessEntity();
    }

    @Override
    public ServerEntity optimizedcushions$serverEntity() {
        return ((TrackedEntityAccessor) this).optimizedcushions$accessServerEntity();
    }
}
