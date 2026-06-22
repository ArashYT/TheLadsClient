/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.api.ClientModInitializer
 *  net.fabricmc.loader.api.FabricLoader
 *  net.minecraft.client.gui.components.debug.DebugScreenEntries
 *  net.minecraft.client.gui.components.debug.DebugScreenEntry
 *  org.apache.logging.log4j.LogManager
 *  org.apache.logging.log4j.Logger
 */
package squeek.appleskin;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.resources.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import squeek.appleskin.ModConfig;
import squeek.appleskin.api.AppleSkinApi;
import squeek.appleskin.client.DebugInfoHudEntry;
import squeek.appleskin.client.HUDOverlayHandler;
import squeek.appleskin.client.TooltipOverlayHandler;
import squeek.appleskin.network.ClientSyncHandler;

public class AppleSkin
implements ClientModInitializer {
    public static final Logger LOGGER = LogManager.getLogger();

    public void onInitializeClient() {
        ClientSyncHandler.init();
        ModConfig.init();
        HUDOverlayHandler.init();
        TooltipOverlayHandler.init();
        FabricLoader.getInstance().getEntrypointContainers("appleskin", AppleSkinApi.class).forEach(entrypoint -> {
            try {
                ((AppleSkinApi)entrypoint.getEntrypoint()).registerEvents();
            }
            catch (Throwable e) {
                LOGGER.error("Failed to load entrypoint for mod {}", (Object)entrypoint.getProvider().getMetadata().getId(), (Object)e);
            }
        });
        DebugScreenEntries.register((Identifier)DebugInfoHudEntry.ENTRY_ID, (DebugScreenEntry)new DebugInfoHudEntry());
    }
}

