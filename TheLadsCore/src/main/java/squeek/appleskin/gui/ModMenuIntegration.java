/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.terraformersmc.modmenu.api.ConfigScreenFactory
 *  com.terraformersmc.modmenu.api.ModMenuApi
 *  me.shedaniel.autoconfig.AutoConfigClient
 *  net.fabricmc.api.EnvType
 *  net.fabricmc.api.Environment
 *  net.fabricmc.loader.api.FabricLoader
 */
package squeek.appleskin.gui;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.autoconfig.AutoConfigClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import squeek.appleskin.gui.AutoConfigIntegration;

@Environment(value=EnvType.CLIENT)
public class ModMenuIntegration
implements ModMenuApi {
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        if (FabricLoader.getInstance().isModLoaded("cloth-config")) {
            return parent -> (Screen)((Object)((Object)AutoConfigClient.getConfigScreen(AutoConfigIntegration.class, (Screen)parent).get()));
        }
        throw new RuntimeException("cloth-config not loaded");
    }
}

