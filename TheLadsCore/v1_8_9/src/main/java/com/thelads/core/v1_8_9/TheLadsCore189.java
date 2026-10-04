package com.thelads.core.v1_8_9;

import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.mods.CoreCatalogExporter;
import com.thelads.core.modules.OldAnimationsModule;
import com.thelads.core.v1_8_9.adapter.VanillaGameBridge189;
import com.thelads.core.v1_8_9.feature.AutoReconnect189;
import com.thelads.core.v1_8_9.feature.Borderless189;
import com.thelads.core.v1_8_9.feature.Clumps189;
import com.thelads.core.v1_8_9.feature.ConnectionTweaks189;
import com.thelads.core.v1_8_9.feature.CoreProbe;
import com.thelads.core.v1_8_9.feature.Crosshair189;
import com.thelads.core.v1_8_9.feature.FoodOverlay189;
import com.thelads.core.v1_8_9.feature.KillBanner189;
import com.thelads.core.v1_8_9.feature.NativeHud;
import com.thelads.core.v1_8_9.feature.NativeMenuKey;
import com.thelads.core.v1_8_9.feature.OldAnimations189;
import com.thelads.core.v1_8_9.feature.RawMouse189;
import com.thelads.core.v1_8_9.feature.RenderScale189;
import com.thelads.core.v1_8_9.feature.Screenshots189;
import com.thelads.core.v1_8_9.feature.SignalLoss189;
import com.thelads.core.v1_8_9.feature.TabTweaks189;
import com.thelads.core.v1_8_9.feature.Tooltips189;
import com.thelads.core.v1_8_9.feature.Zoom189;
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
        com.thelads.core.v1_8_9.feature.Nametags189.register();
        OldAnimations189.register();
        ClientRegistry.registerKeyBinding(Zoom189.ZOOM);
        ClientRegistry.registerKeyBinding(NativeMenuKey.MODULES);
        MinecraftForge.EVENT_BUS.register(new Zoom189());
        MinecraftForge.EVENT_BUS.register(new NativeMenuKey());
        MinecraftForge.EVENT_BUS.register(new NativeHud());
        Crosshair189.register();
        MinecraftForge.EVENT_BUS.register(new Crosshair189());
        AutoReconnect189.register();
        MinecraftForge.EVENT_BUS.register(new ConnectionTweaks189());
        MinecraftForge.EVENT_BUS.register(new KillBanner189());
        MinecraftForge.EVENT_BUS.register(LadsTitleScreen189.INSTANCE);
        MinecraftForge.EVENT_BUS.register(new Tooltips189());
        MinecraftForge.EVENT_BUS.register(new Clumps189());
        MinecraftForge.EVENT_BUS.register(new SignalLoss189());
        MinecraftForge.EVENT_BUS.register(new FoodOverlay189());
        MinecraftForge.EVENT_BUS.register(new RenderScale189());
        Screenshots189.register();
        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("TheLadsCore 1.8.9 initialized successfully.");
    }

    public static final String[] GAMEPLAY_MODULES = {
        "FarBlockEntities", "EnhancedTooltips", "Clumps", "SignalLoss", "AppleSkin", "RenderScale", "BetterScreenshots",
        "Fullbright", "ToggleSprint", "ToggleSneak", "Zoom", "LegacySwing",
        "VerticalBobbing", "OldDamageTilt", "ClientTools", "ParticleBudget", "SmoothHotbar", "TitleScreen", "Title Scale",
        "RawInput", "BorderlessFullscreen",
        // ThreadPriorityModule (common) knows 1.8.9's thread names. DiscordRPC: the "Soon" card, as on the other versions, whose
        // presence sends nothing yet (no Discord connection is made).
        "Threads", "DiscordRPC",
        // EnumValues189 (Jasione); its status says a toggle applies after a restart (registerStatuses).
        "Jasione",
        // TabTweaks189 through GuiPlayerTabOverlayMixin, as 26.x NativeTabTweaks.
        "PingView", "TabList",
        // Nametags189: shadow, nicknames, own display name and backgrounds (also Essential's).
        "Nametags",
        // OldAnimations189 through the ItemRenderer, EntityRenderer, LayerHeldItem, ModelBiped, LayerArmorBase, RenderEntityItem,
        // RenderFish and GuiIngameForge mixins; the menu hides No attack-cooldown dip and Low Shield, which 1.8.9 has nothing for.
        OldAnimationsModule.NAME
    };

    /** Built in, with what Minecraft 1.8.9 itself lacks for some of their options (the launcher shows it). */
    public static final String[][] LIMITED = {
        // Chat189 through GuiNewChatMixin, as 1.21.11 ChatMixin.
        {"Chat", "Minecraft 1.8.9 chat is unsigned, so Hide Signing Indicators has nothing to hide."},
        // MessageDeserializerMixin and NetworkManagerMixin, as 26.x PacketDecodeGuardMixin and PacketErrorGuardMixin.
        {"IgnorePacketErrors", "Minecraft 1.8.9 already keeps playing when a packet fails on the game thread; this covers packets "
            + "that can't be decoded and handlers that fail on the network thread."},
        // Crosshair189 through Forge's crosshair overlay event, as 26.x NativeCrosshair.
        {"Crosshair Tweaks", "Minecraft 1.8.9 has no attack cooldown, item cooldowns or spyglass, so the attack indicator, Dynamic "
            + "Attack Gap, Item Cooldown and spyglass options have nothing to show."},
        // AutoReconnect189 through Forge's screen and tick events and GuiDisconnectedAccessor, as 26.x NativeReconnect.
        {"AutoReconnect", "Realms no longer accept Minecraft 1.8.9, so it reconnects to servers and local worlds; 1.8.9 chat is unsigned, "
            + "so Sign Configured Commands has nothing to sign, and it sends at most 100 characters per action message."},
        // KillBanner189 through Forge's attack and chat events and NetHandlerPlayClientMixin, as 26.x NativeKillBanner.
        {"KillBanner", "Minecraft 1.8.9 sends no damage events, so a kill counts when your own blow, or a server kill message after it, "
            + "finishes the target; arrows and other indirect kills do not."}
    };

    static void registerStatuses() {
        ModuleSupport.registerBuiltIn(NativeHud.MODULES);
        ModuleSupport.registerBuiltIn(GAMEPLAY_MODULES);
        ModuleSupport.registerBuiltInRestart("Jasione");
        for (String[] module : MOD_BACKED)
            ModuleSupport.registerUnavailable(module[0], "Built on " + module[1] + ", which The Lads Client does not include for Minecraft 1.8.9.");
        ModuleSupport.registerUnavailable("DisableNarrator", "Minecraft 1.8.9 has no narrator.");
        ModuleSupport.registerUnavailable("ShulkerBoxUtils", "Minecraft 1.8.9 has no shulker boxes; they were added in Minecraft 1.11.");
        for (String[] module : LIMITED) ModuleSupport.registerBuiltInLimited(module[0], module[1]);
        // The launcher's 1.8.9 pack includes Resourcify (its own in-game browser, no Lads settings page).
        ModuleSupport.registerExternal("Resourcify", "Resourcify", "resourcify", net.minecraftforge.fml.common.Loader.isModLoaded("resourcify"));
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        NativeMenuKey.tick();
        Minecraft mc = Minecraft.getMinecraft();
        OldAnimations189.tick(mc);
        Zoom189.tick(mc);
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
        com.thelads.core.v1_8_9.feature.EnumValues189.reportOnce();
        com.thelads.core.shared.LogProbe.tick(true, mc::shutdown);
        if (Boolean.getBoolean("thelads.verify189Core")) CoreProbe.tick();
        if (Boolean.getBoolean("thelads.verifyAddServer")) com.thelads.core.v1_8_9.feature.AddServerProbe189.tick();
    }

    @SubscribeEvent
    public void frame(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            RawMouse189.paceFrame();
            com.thelads.core.v1_8_9.gui.SmoothScroll189.frame();
        } else Zoom189.frame();
    }

    @SubscribeEvent
    public void open(net.minecraftforge.client.event.GuiOpenEvent event) {
        event.gui = com.thelads.core.v1_8_9.gui.ControlsScreen189.replace(event.gui);
    }
}
