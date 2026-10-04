package com.thelads.core.v26_2.feature;

import com.google.gson.JsonParser;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.config.SliderOption;
import com.thelads.core.modules.RaisedModule;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.slf4j.LoggerFactory;

/**
 * Raised on 26.x ({@link RaisedModule}): how far NativeHudMixin moves vanilla's hotbar group and action bar, and ClientToolsChatMixin
 * the chat. Stands down while the Raised mod is installed.
 */
public final class Raised26 {
    private static final boolean MOD = FabricLoader.getInstance().isModLoaded("raised");
    private static final Identifier SELECTION = Identifier.withDefaultNamespace("textures/gui/sprites/hud/hotbar_selection.png");
    private static boolean probed;
    private Raised26() {}

    /**
     * After NativeQualityOfLife lists Raised as built in (and Lads settings are loaded): an installed Raised mod is listed instead.
     * A 1.6.0 layout file is carried over once, then kept beside as lads-raised.json.migrated.
     */
    static void register() {
        if (MOD) {
            ModuleSupport.registerExternal(RaisedModule.NAME, "Raised", "raised", true);
            return;
        }
        Path layout = FabricLoader.getInstance().getConfigDir().resolve("lads-raised.json");
        var raised = RaisedModule.get();
        if (raised == null || !Files.isRegularFile(layout)) return;
        try {
            raised.adoptLayout(JsonParser.parseString(Files.readString(layout)).getAsJsonObject());
            ConfigManager.save();
            Files.move(layout, layout.resolveSibling("lads-raised.json.migrated"), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception failure) {
            LoggerFactory.getLogger("TheLadsCore").warn("Raised: the 1.6.0 layout in {} was not carried over; it is left as it was", layout, failure);
        }
    }

    /** GUI pixels the hotbar, the bars on it and the action bar move up now. */
    public static int hotbar() {
        var raised = RaisedModule.get();
        return MOD || raised == null ? 0 : raised.hotbarLift(Minecraft.getInstance().gui.screen() instanceof ChatScreen);
    }

    /** GUI pixels chat moves up. */
    public static int chat() {
        var raised = RaisedModule.get();
        return MOD || raised == null ? 0 : raised.chatLift();
    }

    /**
     * Vanilla's selection frame is one row shorter than the 24 it surrounds, which only shows once the hotbar leaves the bottom
     * edge: draw the frame's top row again as its bottom row. A frame already drawn 24 tall (Hovering Hotbar) is complete.
     */
    public static void selectionBottom(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        if (height < 24 && hotbar() > 0) graphics.blit(RenderPipelines.GUI_TEXTURED, SELECTION, x, y + height, 0, 0, width, 1, 24, 23);
    }

    /** QA only (thelads.verifyIntegrations, once on the title screen): the module, its amounts and the mixins that apply them. */
    static void titleProbe() {
        var mc = Minecraft.getInstance();
        if (probed || MOD || !Boolean.getBoolean("thelads.verifyIntegrations") || !(mc.gui.screen() instanceof TitleScreen)) return;
        probed = true;
        var raised = RaisedModule.get();
        int passed = 0;
        var saved = new com.google.gson.JsonElement[3];
        String[] names = {"Hotbar", "Distance", "Chat"};
        boolean enabled = raised != null && raised.isEnabled();
        long modified = raised == null ? 0 : raised.getLastModified();
        try {
            for (int i = 0; i < 3; i++) saved[i] = raised.getOption(names[i]).save();
            passed += check(ModuleSupport.isBuiltIn(RaisedModule.NAME), "Raised is built in");
            for (String name : names) ((SliderOption) raised.getOption(name)).reset();
            raised.setEnabled(true);
            passed += check(hotbar() == 2 && chat() == 0, "defaults: hotbar 2 off the edge, chat where vanilla draws it");
            ((SliderOption) raised.getOption("Chat")).setValue(9);
            passed += check(chat() == 9, "chat lift follows its slider");
            raised.setEnabled(false);
            passed += check(hotbar() == 0 && chat() == 0, "off: vanilla positions");
            passed += check(Arrays.stream(Hud.class.getDeclaredMethods()).anyMatch(m -> m.getName().contains("lads$raiseOverlay")),
                "the action bar mixin is applied");
            passed += check(Arrays.stream(ChatComponent.class.getDeclaredMethods()).anyMatch(m -> m.getName().contains("ladsRaiseChat")),
                "the chat mixin is applied");
            LoggerFactory.getLogger("TheLadsCore").info("Lads raised title probe END: {} passed, 0 failed", passed);
        } catch (Throwable failure) {
            LoggerFactory.getLogger("TheLadsCore").error("Lads raised title probe FAILED after {} checks", passed, failure);
        } finally {
            if (raised != null) {
                for (int i = 0; i < 3; i++) if (saved[i] != null) raised.getOption(names[i]).load(saved[i]);
                raised.setEnabled(enabled);
                raised.setLastModified(modified);
            }
        }
    }

    private static int check(boolean condition, String text) {
        if (!condition) throw new IllegalStateException(text);
        return 1;
    }
}
