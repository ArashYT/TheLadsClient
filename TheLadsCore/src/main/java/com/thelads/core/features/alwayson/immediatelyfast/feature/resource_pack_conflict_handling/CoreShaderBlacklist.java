/*
 * Decompiled with CFR 0.152.
 */
package com.thelads.core.features.alwayson.immediatelyfast.feature.resource_pack_conflict_handling;

import java.util.Set;
import net.minecraft.resources.Identifier;

public class CoreShaderBlacklist {
    private static final Set<Identifier> BLACKLIST = Set.of(Identifier.withDefaultNamespace("core/position_color"), Identifier.withDefaultNamespace("core/position_tex"), Identifier.withDefaultNamespace("core/position_tex_color"), Identifier.withDefaultNamespace("core/rendertype_text"), Identifier.withDefaultNamespace("core/rendertype_text_background"), Identifier.withDefaultNamespace("core/rendertype_text_background_see_through"), Identifier.withDefaultNamespace("core/rendertype_text_intensity"), Identifier.withDefaultNamespace("core/rendertype_text_intensity_see_through"), Identifier.withDefaultNamespace("core/rendertype_text_see_through"), Identifier.withDefaultNamespace("core/rendertype_item_entity_translucent_cull"));

    public static Set<Identifier> getBlacklist() {
        return BLACKLIST;
    }
}

