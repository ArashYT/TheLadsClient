package com.thelads.core.v26_2.feature.food;

import com.thelads.core.config.ModuleSupport;
import com.thelads.core.v26_2.feature.food.client.DebugInfoHudEntry;
import com.thelads.core.v26_2.feature.food.client.HUDOverlayHandler;
import com.thelads.core.v26_2.feature.food.client.TooltipOverlayHandler;
import com.thelads.core.v26_2.feature.food.network.ClientSyncHandler;
import com.thelads.core.v26_2.feature.food.network.SyncHandler;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;

/** Food overlays live in Core. A manually installed upstream mod keeps ownership to avoid doubles. */
public final class NativeFoodOverlay {
    private static boolean active;
    private NativeFoodOverlay() {}
    public static boolean active() { return active; }
    public static void initialize() {
        if (active || FabricLoader.getInstance().isModLoaded("appleskin")) return;
        active = true;
        ModuleSupport.registerBuiltIn("AppleSkin");
        FoodOverlayConfig.refresh();
        SyncHandler.init();
        ClientSyncHandler.init();
        HUDOverlayHandler.init();
        TooltipOverlayHandler.init();
        DebugScreenEntries.register(DebugInfoHudEntry.ENTRY_ID, new DebugInfoHudEntry());
    }
}
