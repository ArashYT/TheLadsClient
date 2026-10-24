package com.thelads.core.modules;

import com.thelads.core.config.Module;

/**
 * Custom Item Textures: the item textures resource packs ship in OptiFine's CIT format (client.CustomItems), drawn where nothing else
 * does: 1.8.9 without OptiFine (Ionium). With OptiFine, OptiFine's own Custom Items setting draws them. On by default, as OptiFine's:
 * without a CIT pack it changes nothing. Visual only.
 */
public final class CustomItemTexturesModule extends Module {
    public static final String NAME = "Custom Item Textures";

    public CustomItemTexturesModule() {
        super(NAME, "Shows the custom item textures resource packs include (OptiFine CIT format): swords, potions, bows and more by name, "
            + "enchantment or damage.");
        setEnabled(true);
    }
}
