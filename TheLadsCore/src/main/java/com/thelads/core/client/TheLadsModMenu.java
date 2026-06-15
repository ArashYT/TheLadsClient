package com.thelads.core.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import com.thelads.core.client.gui.LadsSettingsScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * ModMenu integration: makes The Lads Core's config button in ModMenu open the
 * same Right Shift settings screen. Only loaded client-side by ModMenu.
 */
@Environment(EnvType.CLIENT)
public class TheLadsModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new LadsSettingsScreen(parent);
    }
}
