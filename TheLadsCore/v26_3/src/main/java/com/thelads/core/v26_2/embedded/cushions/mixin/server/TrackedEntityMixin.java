// Derived from Optimized Cushions 1.0.0 (tag 1.0.0) by NikitaCartes (MIT); see META-INF/lads-sources/cushions/LICENSE.
package com.thelads.core.v26_2.embedded.cushions.mixin.server;

import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import com.thelads.core.v26_2.embedded.cushions.server.TrackedEntityExt;

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class TrackedEntityMixin implements TrackedEntityExt {
    @Shadow
    @Final
    private ServerEntity serverEntity;

    @Shadow
    @Final
    private Entity entity;

    @Override
    public Entity optimizedcushions$entity() {
        return this.entity;
    }

    @Override
    public ServerEntity optimizedcushions$serverEntity() {
        return this.serverEntity;
    }
}
