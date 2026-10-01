package com.thelads.core.v1_8_9;

import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.mods.CoreCatalogExporter;
import com.thelads.core.v1_8_9.adapter.VanillaGameBridge189;
import com.thelads.core.v1_8_9.feature.CoreProbe;
import com.thelads.core.v1_8_9.feature.NativeHud;
import com.thelads.core.v1_8_9.feature.NativeMenuKey;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;

@Mod(modid = "theladscore", name = "The Lads Core", clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]")
public class TheLadsCore189 {
    private static final Logger LOGGER = LogManager.getLogger("TheLadsCore-1.8.9");
    /** Built on third-party mods The Lads Client does not ship for 1.8.9: listed as unavailable with that reason. */
    private static final String[][] MOD_BACKED = {
        {"Performance", "Sodium"}, {"Lithium", "Lithium"}, {"FerriteCore", "FerriteCore"}, {"EntityCulling", "Entity Culling"},
        {"ImmediatelyFast", "ImmediatelyFast"}, {"ScalableLux", "ScalableLux"}, {"Exordium", "Exordium"}, {"DynamicFPS", "Dynamic FPS"},
        {"DynamicLights", "LambDynamicLights"}, {"SkinLayers", "3D Skin Layers"}, {"NotEnoughAnimations", "Not Enough Animations"},
        {"BetterF3", "BetterF3"}, {"BetterStats", "Better Statistics Screen"},
        {"JEI (Just Enough Items)", "Just Enough Items"}, {"XaeroMinimap", "Xaero's Minimap"}, {"XaeroWorldmap", "Xaero's World Map"},
        {"Minimap", "Xaero's Minimap"}, {"Jade", "Jade"}, {"ModernAdvancements", "Modern Advancements"},
        {"EnhancedToolbars", "Durability Tooltip"}, {"Capes", "Capes"}, {"Raised", "Raised"}};

    private static Field equippedProgressField;
    private static Field prevEquippedProgressField;
    private static boolean rawMouseInstalled = false;
    private static boolean borderlessApplied = false;

    static {
        try {
            equippedProgressField = net.minecraftforge.fml.relauncher.ReflectionHelper.findField(
                net.minecraft.client.renderer.ItemRenderer.class, "equippedProgress", "field_78454_c");
            prevEquippedProgressField = net.minecraftforge.fml.relauncher.ReflectionHelper.findField(
                net.minecraft.client.renderer.ItemRenderer.class, "prevEquippedProgress", "field_78451_d");
        } catch (Throwable ignored) {}
    }

    public TheLadsCore189() {
        // Shared parity enabled: worlds and packs share global paths with newer versions
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        LOGGER.info("Initializing TheLadsCore for Minecraft 1.8.9...");
        try {
            net.minecraftforge.common.ForgeModContainer.disableVersionCheck = true;
            org.lwjgl.opengl.Display.setTitle("The Lads Client 1.4.2");
        } catch (Throwable ignored) {}
        LadsGameBridge.set(new VanillaGameBridge189());
        ConfigManager.load();
        registerStatuses();
        ClientRegistry.registerKeyBinding(NativeMenuKey.MODULES);
        MinecraftForge.EVENT_BUS.register(new NativeMenuKey());
        MinecraftForge.EVENT_BUS.register(new NativeHud());
        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("TheLadsCore 1.8.9 initialized successfully.");
    }

    private static final String[] GAMEPLAY_MODULES = {
        "Fullbright", "ToggleSprint", "ToggleSneak", "Zoom", "Crosshair", "OldAnimations", "LegacySwing",
        "VerticalBobbing", "OldDamageTilt", "ClientTools", "ParticleBudget", "SmoothHotbar", "TitleScreen", "Title Scale",
        "RawInput", "BorderlessFullscreen"
    };

    static void registerStatuses() {
        ModuleSupport.registerBuiltIn(NativeHud.MODULES);
        ModuleSupport.registerBuiltIn(GAMEPLAY_MODULES);
        for (String[] module : MOD_BACKED)
            ModuleSupport.registerUnavailable(module[0], "Built on " + module[1] + ", which The Lads Client does not include for Minecraft 1.8.9.");
        ModuleSupport.registerUnavailable("HideChatIndicators", "Minecraft 1.8.9 has no chat signing, so there are no indicators to hide.");
        ModuleSupport.registerUnavailable("DisableNarrator", "Minecraft 1.8.9 has no narrator.");
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
            com.thelads.core.config.Module legacySwing = com.thelads.core.config.ModuleManager.getInstance().getModule("LegacySwing");
            if (legacySwing != null && legacySwing.isEnabled() && mc.getItemRenderer() != null) {
                try {
                    if (mc.thePlayer.isSwingInProgress) {
                        if (equippedProgressField != null) equippedProgressField.setFloat(mc.getItemRenderer(), 1.0f);
                        if (prevEquippedProgressField != null) prevEquippedProgressField.setFloat(mc.getItemRenderer(), 1.0f);
                    }
                } catch (Throwable ignored) {}
            }
        }

        try {
            String title = org.lwjgl.opengl.Display.getTitle();
            if (title == null || !title.startsWith("The Lads Client 1.4.2")) {
                org.lwjgl.opengl.Display.setTitle("The Lads Client 1.4.2");
            }
        } catch (Throwable ignored) {}

        com.thelads.core.config.Module rawInput = com.thelads.core.config.ModuleManager.getInstance().getModule("RawInput");
        if (rawInput != null && rawInput.isEnabled()) {
            ensureRawMouseHelper(mc);
        }

        com.thelads.core.config.Module borderless = com.thelads.core.config.ModuleManager.getInstance().getModule("BorderlessFullscreen");
        if (borderless != null && borderless.isEnabled()) {
            applyBorderlessFullscreen(mc);
        }

        CoreCatalogExporter.exportIfChanged();
        if (Boolean.getBoolean("thelads.verify189Core")) CoreProbe.tick();
    }

    private static void ensureRawMouseHelper(Minecraft mc) {
        if (rawMouseInstalled || mc.mouseHelper == null) return;
        try {
            net.java.games.input.Controller[] controllers = net.java.games.input.ControllerEnvironment.getDefaultEnvironment().getControllers();
            net.java.games.input.Controller mouse = null;
            for (net.java.games.input.Controller c : controllers) {
                if (c.getType() == net.java.games.input.Controller.Type.MOUSE) {
                    mouse = c;
                    break;
                }
            }
            if (mouse != null) {
                final net.java.games.input.Controller finalMouse = mouse;
                net.java.games.input.Component xC = null, yC = null;
                for (net.java.games.input.Component comp : mouse.getComponents()) {
                    if (comp.getIdentifier() == net.java.games.input.Component.Identifier.Axis.X) xC = comp;
                    if (comp.getIdentifier() == net.java.games.input.Component.Identifier.Axis.Y) yC = comp;
                }
                final net.java.games.input.Component finalX = xC, finalY = yC;
                if (finalX != null && finalY != null) {
                    mc.mouseHelper = new net.minecraft.util.MouseHelper() {
                        @Override
                        public void mouseXYChange() {
                            finalMouse.poll();
                            this.deltaX = (int) finalX.getPollData();
                            this.deltaY = -(int) finalY.getPollData();
                        }
                    };
                    rawMouseInstalled = true;
                    LOGGER.info("Raw mouse input initialized via JInput");
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("Could not initialize raw mouse input: " + t.getMessage());
        }
    }

    private static void applyBorderlessFullscreen(Minecraft mc) {
        try {
            if (mc.gameSettings.fullScreen && !borderlessApplied) {
                System.setProperty("org.lwjgl.opengl.Window.undecorated", "true");
                org.lwjgl.opengl.Display.setDisplayMode(org.lwjgl.opengl.Display.getDesktopDisplayMode());
                org.lwjgl.opengl.Display.setLocation(0, 0);
                org.lwjgl.opengl.Display.setFullscreen(false);
                borderlessApplied = true;
            } else if (!mc.gameSettings.fullScreen && borderlessApplied) {
                System.setProperty("org.lwjgl.opengl.Window.undecorated", "false");
                borderlessApplied = false;
            }
        } catch (Throwable ignored) {}
    }

    @SubscribeEvent
    public void fov(net.minecraftforge.client.event.EntityViewRenderEvent.FOVModifier event) {
        com.thelads.core.config.Module zoom = com.thelads.core.config.ModuleManager.getInstance().getModule("Zoom");
        if (zoom != null && zoom.isEnabled() && org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_C)) {
            event.setFOV(event.getFOV() * 0.3f);
        }
    }
}
