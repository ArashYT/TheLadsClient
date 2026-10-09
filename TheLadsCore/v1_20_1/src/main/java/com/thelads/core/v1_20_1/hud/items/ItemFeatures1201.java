package com.thelads.core.v1_20_1.hud.items;

import com.thelads.core.config.ModuleSupport;
import com.thelads.core.v1_20_1.hud.Mods1201;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * hud-items group: AppleSkin (Food1201), EnhancedTooltips and EnhancedToolbars' durability line (Tooltips1201) and ShulkerBoxUtils
 * (ShulkerInventory1201, ShulkerContents1201). The drawing hooks are the mixins in theladscore.v1_20_1.hud.items.mixins.json.
 */
public final class ItemFeatures1201 {
    private ItemFeatures1201() {}

    public static void register() {
        Food1201.register();
        // A loaded Durability Tooltip jar keeps the durability line (as on 26.3); the module card then shows that mod.
        if (Mods1201.loaded("durabilitytooltip")) ModuleSupport.registerExternal("EnhancedToolbars", "Durability Tooltip", "durabilitytooltip", true);
        else ModuleSupport.registerBuiltIn("EnhancedToolbars");
        ModuleSupport.registerBuiltIn("EnhancedTooltips", "ShulkerBoxUtils");
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ShulkerContents1201.tick();
            ItemsProbe1201.tick(client);
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ShulkerObservations1201.stopping());
    }
}
