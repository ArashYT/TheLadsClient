package com.thelads.core.v26_2.feature.async.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.thelads.core.v26_2.feature.async.AsyncTicking;
import java.util.Optional;
import net.minecraft.world.level.chunk.storage.SectionStorage;
import org.spongepowered.asm.mixin.Mixin;

/** Section lookups load missing sections into a shared map (the POI storage's other entry points). */
@Mixin(SectionStorage.class)
abstract class SectionStorageAsyncMixin<R> {
    @WrapMethod(method = "getOrLoad")
    private Optional<R> lads$lockLoad(long section, Operation<Optional<R>> original) {
        return AsyncTicking.locked(original, section);
    }
}
