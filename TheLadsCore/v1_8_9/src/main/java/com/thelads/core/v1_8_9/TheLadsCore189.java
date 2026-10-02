package com.thelads.core.v1_8_9;

import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.mods.CoreCatalogExporter;
import com.thelads.core.v1_8_9.adapter.VanillaGameBridge189;
import com.thelads.core.v1_8_9.feature.Borderless189;
import com.thelads.core.v1_8_9.feature.CoreProbe;
import com.thelads.core.v1_8_9.feature.NativeHud;
import com.thelads.core.v1_8_9.feature.NativeMenuKey;
import com.thelads.core.v1_8_9.feature.RawMouse189;
import com.thelads.core.v1_8_9.feature.TabTweaks189;
import com.thelads.core.v1_8_9.gui.LadsTitleScreen189;
import net.minecraft.client.Minecraft;
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
    public static final String[][] MOD_BACKED = {
        {"Performance", "Sodium"}, {"Lithium", "Lithium"}, {"FerriteCore", "FerriteCore"},
        {"ScalableLux", "ScalableLux"}, {"Exordium", "Exordium"}, {"DynamicFPS", "Dynamic FPS"},
        {"DynamicLights", "LambDynamicLights"}, {"SkinLayers", "3D Skin Layers"}, {"NotEnoughAnimations", "Not Enough Animations"},
        {"BetterF3", "BetterF3"}, {"BetterStats", "Better Statistics Screen"},
        {"JEI (Just Enough Items)", "Just Enough Items"}, {"XaeroMinimap", "Xaero's Minimap"}, {"XaeroWorldmap", "Xaero's World Map"},
        {"Minimap", "Xaero's Minimap"}, {"Jade", "Jade"}, {"ModernAdvancements", "Modern Advancements"},
        {"EnhancedToolbars", "Durability Tooltip"}, {"Raised", "Raised"}};

    private static String windowTitle = "The Lads Client";

    public TheLadsCore189() {
        // JInput's jar is sealed: LaunchClassLoader defining its classes logs "has a security seal ... not secure" once per
        // class. It loads from the parent loader instead, as org.lwjgl. does. Set before RawMouse189 (or any JInput class) loads.
        net.minecraft.launchwrapper.Launch.classLoader.addClassLoaderExclusion("net.java.games.input.");
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        LOGGER.info("Initializing TheLadsCore for Minecraft 1.8.9...");
        try {
            net.minecraftforge.common.ForgeModContainer.disableVersionCheck = true;
            windowTitle = com.thelads.core.LadsVersion.clientName();
            org.lwjgl.opengl.Display.setTitle(windowTitle);
        } catch (Throwable ignored) {}
        LadsGameBridge.set(new VanillaGameBridge189());
        ConfigManager.load();
        registerStatuses();
        ClientRegistry.registerKeyBinding(NativeMenuKey.MODULES);
        MinecraftForge.EVENT_BUS.register(new NativeMenuKey());
        MinecraftForge.EVENT_BUS.register(new NativeHud());
        MinecraftForge.EVENT_BUS.register(LadsTitleScreen189.INSTANCE);
        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("TheLadsCore 1.8.9 initialized successfully.");
    }

    public static final String[] GAMEPLAY_MODULES = {
        "Fullbright", "ToggleSprint", "ToggleSneak", "Zoom", "LegacySwing",
        "VerticalBobbing", "OldDamageTilt", "ClientTools", "ParticleBudget", "SmoothHotbar", "TitleScreen", "Title Scale",
        "RawInput", "BorderlessFullscreen",
        // ThreadPriorityModule (common) knows 1.8.9's thread names. DiscordRPC: the "Soon" card, as on the other versions, whose
        // presence sends nothing yet (no Discord connection is made).
        "Threads", "DiscordRPC",
        // TabTweaks189 through GuiPlayerTabOverlayMixin, as 26.x NativeTabTweaks.
        "PingView", "TabList"
    };

    static void registerStatuses() {
        ModuleSupport.registerBuiltIn(NativeHud.MODULES);
        ModuleSupport.registerBuiltIn(GAMEPLAY_MODULES);
        for (String[] module : MOD_BACKED)
            ModuleSupport.registerUnavailable(module[0], "Built on " + module[1] + ", which The Lads Client does not include for Minecraft 1.8.9.");
        ModuleSupport.registerUnavailable("DisableNarrator", "Minecraft 1.8.9 has no narrator.");
        // The launcher's 1.8.9 pack includes Resourcify (its own in-game browser, no Lads settings page).
        ModuleSupport.registerExternal("Resourcify", "Resourcify", "resourcify", net.minecraftforge.fml.common.Loader.isModLoaded("resourcify"));
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        NativeMenuKey.tick();
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) {
            com.thelads.core.config.Module fullbright = com.thelads.core.config.ModuleManager.getInstance().getModule("Fullbright");
            if (fullbright != null && fullbright.isEnabled() && mc.gameSettings.gammaSetting < 15.0f) {
                mc.gameSettings.gammaSetting = 100.0f;
            }
            com.thelads.core.config.Module toggleSprint = com.thelads.core.config.ModuleManager.getInstance().getModule("ToggleSprint");
            if (toggleSprint != null && toggleSprint.isEnabled() && mc.thePlayer.movementInput != null) {
                if (mc.thePlayer.movementInput.moveForward > 0 && !mc.thePlayer.isSneaking()
                    && !mc.thePlayer.isCollidedHorizontally && mc.thePlayer.getFoodStats().getFoodLevel() > 6) {
                    mc.thePlayer.setSprinting(true);
                }
            }
        }

        try {
            String title = org.lwjgl.opengl.Display.getTitle();
            if (title == null || !title.startsWith(windowTitle)) {
                org.lwjgl.opengl.Display.setTitle(windowTitle);
            }
        } catch (Throwable ignored) {}

        RawMouse189.install(mc);
        TabTweaks189.refresh();

        Borderless189.tick(mc);

        CoreCatalogExporter.exportIfChanged();
        if (Boolean.getBoolean("thelads.verify189Core")) CoreProbe.tick();
    }

    @SubscribeEvent
    public void frame(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) RawMouse189.paceFrame();
    }

    @SubscribeEvent
    public void fov(net.minecraftforge.client.event.EntityViewRenderEvent.FOVModifier event) {
        com.thelads.core.config.Module zoom = com.thelads.core.config.ModuleManager.getInstance().getModule("Zoom");
        if (zoom != null && zoom.isEnabled() && org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_C)) {
            event.setFOV(event.getFOV() * 0.3f);
        }
    }
}
