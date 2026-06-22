/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.terraformersmc.modmenu.api.ConfigScreenFactory
 *  com.terraformersmc.modmenu.api.ModMenuApi
 */
package dev.ultimatchamp.enhancedtooltips.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.ultimatchamp.enhancedtooltips.config.EnhancedTooltipsConfig;

public class ModMenuAPIImpl
implements ModMenuApi {
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return EnhancedTooltipsConfig::createConfigScreen;
    }
}

