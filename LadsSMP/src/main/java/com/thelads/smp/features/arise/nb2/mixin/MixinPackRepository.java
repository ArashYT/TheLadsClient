package com.thelads.smp.features.arise.nb2.mixin;

import com.thelads.smp.features.arise.nb2.PaxiManager;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Mixin to PackRepository constructor to inject Paxi global datapack and resource pack sources.
 */
@Mixin(PackRepository.class)
public abstract class MixinPackRepository {

    @ModifyVariable(
            method = "<init>([Lnet/minecraft/server/packs/repository/RepositorySource;)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    private static RepositorySource[] onInitPackSources(RepositorySource[] sources) {
        return PaxiManager.appendSources(sources);
    }
}
