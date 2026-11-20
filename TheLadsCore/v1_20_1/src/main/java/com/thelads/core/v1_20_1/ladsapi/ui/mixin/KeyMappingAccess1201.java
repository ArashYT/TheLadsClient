package com.thelads.core.v1_20_1.ladsapi.ui.mixin;

import java.util.Map;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The name map of every key mapping (server keys are taken out of it when the server drops its actions) and the Controls category sort order. */
@Mixin(KeyMapping.class)
public interface KeyMappingAccess1201 {
    @Accessor("ALL")
    static Map<String, KeyMapping> lads$all() {
        throw new AssertionError();
    }

    @Accessor("CATEGORY_SORT_ORDER")
    static Map<String, Integer> lads$sortOrder() {
        throw new AssertionError();
    }
}
