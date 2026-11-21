package com.thelads.core.v26_2.feature.packs.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.io.IOException;
import net.minecraft.util.eventlog.JsonEventLog;
import org.spongepowered.asm.mixin.Mixin;

/** The download log is written by up to four pack downloads at once now; its writes (seek to end, then append) must not interleave. */
@Mixin(JsonEventLog.class)
public abstract class JsonEventLogLockMixin<T> {
    @WrapMethod(method = "write")
    private void lads$serializeWrites(T event, Operation<Void> original) throws IOException {
        synchronized (this) {
            original.call(event);
        }
    }
}
