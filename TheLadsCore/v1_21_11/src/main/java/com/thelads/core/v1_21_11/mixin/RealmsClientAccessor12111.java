package com.thelads.core.v1_21_11.mixin;

import com.mojang.realmsclient.client.RealmsClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** RealmsClient keeps one instance, with the session it was first made with. */
@Mixin(RealmsClient.class)
public interface RealmsClientAccessor12111 {
    @Accessor("realmsClientInstance")
    static void lads$setInstance(RealmsClient client) {
        throw new AssertionError();
    }
}
