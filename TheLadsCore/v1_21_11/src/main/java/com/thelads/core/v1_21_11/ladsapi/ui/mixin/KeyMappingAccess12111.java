package com.thelads.core.v1_21_11.ladsapi.ui.mixin;

import java.util.Map;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The name map of every key mapping: server keys are taken out of it when the server drops its actions. */
@Mixin(KeyMapping.class)
public interface KeyMappingAccess12111 {
    @Accessor("ALL")
    static Map<String, KeyMapping> lads$all() {
        throw new AssertionError();
    }
}
