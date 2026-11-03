package com.thelads.core.v26_2.mixin;

import com.mojang.realmsclient.client.RealmsClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** RealmsClient keeps one instance, with the session it was first made with. */
@Mixin(RealmsClient.class)
public interface RealmsClientAccessor26 {
    @Accessor("realmsClientInstance")
    static void lads$setInstance(RealmsClient client) {
        throw new AssertionError();
    }
}
