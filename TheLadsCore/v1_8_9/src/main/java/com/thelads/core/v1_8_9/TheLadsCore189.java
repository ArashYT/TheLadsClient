package com.thelads.core.v1_8_9;

import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.mods.CoreCatalogExporter;
import com.thelads.core.shared.SharedContentPaths;
import com.thelads.core.v1_8_9.adapter.VanillaGameBridge189;
import com.thelads.core.v1_8_9.feature.CoreProbe;
import com.thelads.core.v1_8_9.feature.NativeMenuKey;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = "theladscore", name = "The Lads Core", clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]")
public class TheLadsCore189 {
    private static final Logger LOGGER = LogManager.getLogger("TheLadsCore-1.8.9");
    /** Built on third-party mods The Lads Client does not ship for 1.8.9: listed as unavailable with that reason. */
    private static final String[][] MOD_BACKED = {
        {"Performance", "Sodium"}, {"Lithium", "Lithium"}, {"FerriteCore", "FerriteCore"}, {"EntityCulling", "Entity Culling"},
        {"ImmediatelyFast", "ImmediatelyFast"}, {"ScalableLux", "ScalableLux"}, {"Exordium", "Exordium"}, {"DynamicFPS", "Dynamic FPS"},
        {"DynamicLights", "LambDynamicLights"}, {"SkinLayers", "3D Skin Layers"}, {"NotEnoughAnimations", "Not Enough Animations"},
        {"BetterF3", "BetterF3"}, {"BetterStats", "Better Statistics Screen"}, {"Resourcify", "Resourcify"},
        {"JEI (Just Enough Items)", "Just Enough Items"}, {"XaeroMinimap", "Xaero's Minimap"}, {"XaeroWorldmap", "Xaero's World Map"},
        {"Minimap", "Xaero's Minimap"}, {"Jade", "Jade"}, {"ModernAdvancements", "Modern Advancements"},
        {"EnhancedToolbars", "Durability Tooltip"}, {"Capes", "Capes"}, {"Raised", "Raised"}};

    public TheLadsCore189() {
        // Before any Core code runs: 1.8.9 corrupts a newer world, so shared worlds and packs must never be used here.
        SharedContentPaths.isolateWorldsAndPacks();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        LOGGER.info("Initializing TheLadsCore for Minecraft 1.8.9...");
        LadsGameBridge.set(new VanillaGameBridge189());
        ConfigManager.load();
        registerStatuses();
        ClientRegistry.registerKeyBinding(NativeMenuKey.MODULES);
        MinecraftForge.EVENT_BUS.register(new NativeMenuKey());
        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("TheLadsCore 1.8.9 initialized successfully.");
    }

    /**
     * The launcher catalog shows only what works here. Nothing is built in yet (the HUD and the gameplay modules follow);
     * modules built on third-party mods, or on game features 1.8.9 lacks, are unavailable with the reason. The rest stay
     * pending: "not connected to this game version yet".
     */
    static void registerStatuses() {
        for (String[] module : MOD_BACKED)
            ModuleSupport.registerUnavailable(module[0], "Built on " + module[1] + ", which The Lads Client does not include for Minecraft 1.8.9.");
        ModuleSupport.registerUnavailable("HideChatIndicators", "Minecraft 1.8.9 has no chat signing, so there are no indicators to hide.");
        ModuleSupport.registerUnavailable("DisableNarrator", "Minecraft 1.8.9 has no narrator.");
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        NativeMenuKey.tick();
        // The launcher lists Lads modules from this catalog; a later registration bumps the revision and rewrites it.
        CoreCatalogExporter.exportIfChanged();
        if (Boolean.getBoolean("thelads.verify189Core")) CoreProbe.tick();
    }
}
