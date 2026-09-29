package com.thelads.core.v26_2.feature.raised;

import com.thelads.core.config.ActionOption;
import com.thelads.core.v26_2.feature.NativeQualityOfLife;
import com.thelads.core.v26_2.feature.raised.client.gui.group.Group;
import com.thelads.core.v26_2.feature.raised.client.gui.layer.Layer;
import com.thelads.core.v26_2.feature.raised.client.gui.screens.AbstractLayersScreen;
import com.thelads.core.v26_2.feature.raised.client.gui.screens.AddScreen;
import com.thelads.core.v26_2.feature.raised.client.gui.screens.AdditionalSettingsScreen;
import com.thelads.core.v26_2.feature.raised.client.gui.screens.EditScreen;
import com.thelads.core.v26_2.feature.raised.client.gui.screens.SelectScreen;
import com.thelads.core.v26_2.feature.raised.client.gui.components.IntRangeSliderButton;
import com.thelads.core.v26_2.feature.raised.config.Config;
import com.thelads.core.v26_2.feature.raised.option.Options;
import com.thelads.core.v26_2.feature.raised.option.AdditionalSettings.HotbarSelectionFix;
import com.thelads.core.v26_2.feature.raised.util.Translate;
import java.nio.file.Files;
import java.util.TreeSet;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.resources.Identifier;
import org.slf4j.LoggerFactory;

/** Isolated title-screen checks of real GUI extraction, widgets, matrix transforms and disk round trips. */
public final class NativeRaisedProbe {
    private static boolean done;
    private static int passed;
    public static void tick() {
        var mc = Minecraft.getInstance();
        if (done || !Boolean.getBoolean("thelads.verifyIntegrations") || !NativeRaised.active()
                || !(mc.gui.screen() instanceof TitleScreen)) return;
        done = true;
        var originalOptions = Config.getOptions(); var originalFile = Config.file;
        var originalScreen = mc.gui.screen(); var originalGroup = AbstractLayersScreen.currentGroup;
        var module = NativeQualityOfLife.module("Raised");
        boolean enabled = module.isEnabled(); long modified = module.getLastModified();
        try {
            var game = FabricLoader.getInstance().getGameDir().toRealPath();
            check(game.getParent().getFileName().toString().equals("verification")
                    && game.getParent().getParent().getFileName().toString().equals("artifacts"), "isolated verification directory");
            var directory = Files.createTempDirectory(game.getParent(), "raised-layout-");
            Config.file = directory.resolve("layout.json").toFile();
            var options = Config.gson.fromJson(Config.gson.toJson(originalOptions), Options.class);
            Config.setOptions(options); options.groups.clear();
            options.layers.put("minecraft:chat", new Layer(Layer.Anchor.BOTTOM_LEFT));
            options.groups.put("First", new Group(new Group.Offset(7, 9), new TreeSet<>(java.util.List.of("minecraft:chat"))));
            options.groups.put("Second", new Group(new Group.Offset(2, 3), new TreeSet<>(java.util.List.of("minecraft:chat"))));
            module.setEnabled(true);
            check(Translate.getX("minecraft:chat") == 9 && Translate.getY("minecraft:chat") == -12, "group offsets combine with anchor directions");
            var matrix = new org.joml.Matrix3x2fStack(4);
            var before = new org.joml.Matrix3x2f(matrix);
            Translate.start(matrix, "minecraft:chat");
            check(matrix.m20() == 9 && matrix.m21() == -12, "native pose applies exact layout");
            Translate.end(matrix, "minecraft:chat");
            check(matrix.equals(before), "native pose restored");
            module.setEnabled(false);
            check(Translate.getX("minecraft:chat") == 0 && Translate.getY("minecraft:chat") == 0, "master disabled returns vanilla positions");
            module.setEnabled(true);
            check(Translate.getX("missing:layer") == 0, "unknown external layer does not crash");
            for (var anchor : Layer.Anchor.values()) {
                options.layers.get("minecraft:chat").setAnchor(anchor);
                check(Translate.getX("minecraft:chat") == 9 * anchor.getX() && Translate.getY("minecraft:chat") == 12 * anchor.getY(), "anchor " + anchor);
            }
            Config.save(); var saved = Files.readString(Config.file.toPath()); Config.load();
            check(Config.gson.toJson(Config.getOptions()).equals(saved), "actual layout storage round trip");
            int ordinary = selection(HotbarSelectionFix.NONE);
            check(ordinary > 0, "ordinary selector extracts native sprite geometry");
            check(selection(HotbarSelectionFix.REPLACE) > 0, "replacement selector sprite resolves");
            check(selection(HotbarSelectionFix.PATCH) == ordinary + 1, "patched selector adds exactly one repaired row");
            var action = (ActionOption) module.getOption("HUD Layout");
            check(action.isAvailable(), "Lads exposes native layout editor");
            action.run();
            check(mc.gui.screen() instanceof SelectScreen, "Lads editor action opens actual layout screen");
            var editor = (SelectScreen) mc.gui.screen();
            check(!editor.children().isEmpty() && editor.leftList.children().size() == 2, "native widgets contain the actual groups");
            editor.resize(320, 240);
            check(editor.controlClose.getWidth() > 0 && editor.controlClose.getX() >= 0, "compact editor retains usable controls");
            check(editor.leftList.getX() == editor.leftPanelX + AbstractLayersScreen.LIST_BORDER
                    && editor.leftList.getY() == editor.leftListY
                    && editor.rightList.getX() == editor.rightPanelX + AbstractLayersScreen.LIST_BORDER
                    && editor.rightList.getY() == editor.rightListY, "compact resize repositions both lists to new panels");
            check(editor.leftList.getX() >= 0 && editor.leftList.getY() >= 0
                    && editor.rightList.getX() + editor.rightList.getWidth() <= 320
                    && editor.leftList.getY() + editor.leftList.getHeight() <= 240
                    && editor.rightList.getY() + editor.rightList.getHeight() <= 240,
                    "compact editor keeps both list viewports inside the screen");
            var state = new GuiRenderState();
            editor.extractRenderState(new GuiGraphicsExtractor(mc, state, 0, 0), 0, 0, 0);
            check(elements(state) > 0, "native editor extracts actual graphics");
            editor.keyPressed(new KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_RSHIFT, 54, com.mojang.blaze3d.platform.InputConstants.MOD_SHIFT));
            check(mc.gui.screen() == originalScreen, "Right Shift returns to editor parent");
            editor.setCurrentGroup(editor.leftList.children().getFirst());
            var edit = new EditScreen(editor);
            mc.gui.setScreen(edit);
            edit.resize(640, 480);
            edit.resize(320, 240);
            check(edit.optionOffsetX.getX() == edit.leftPanelX
                    && edit.optionOffsetX.getY() == edit.leftPanelY + AbstractLayersScreen.WIDGET_AND_GAP_HEIGHT
                    && edit.optionOffsetY.getX() == edit.leftPanelX
                    && edit.optionOffsetY.getY() == edit.leftPanelY + 2 * AbstractLayersScreen.WIDGET_AND_GAP_HEIGHT,
                    "compact edit sliders move to the resized panel");
            ((IntRangeSliderButton) edit.optionOffsetX).setValue(10000);
            ((IntRangeSliderButton) edit.optionOffsetY).setValue(10000);
            check(((IntRangeSliderButton) edit.optionOffsetX).getValue() == 80
                    && ((IntRangeSliderButton) edit.optionOffsetY).getValue() == 60,
                    "compact slider ranges use the new screen dimensions");
            check(Config.getOptions().groups.get(edit.getCurrentGroup().getGroupName()).getOffset().getX() == 80
                    && Config.getOptions().groups.get(edit.getCurrentGroup().getGroupName()).getOffset().getY() == 60,
                    "resized sliders write bounded offsets to the isolated layout");
            check(edit.leftList.getX() == edit.leftPanelX + AbstractLayersScreen.LIST_BORDER
                    && edit.rightList.getY() == edit.rightListY && edit.controlReturn.getX() == edit.leftPanelX,
                    "edit lists and return control move with resized panels");
            var popup = new AddScreen(editor);
            mc.gui.setScreen(popup);
            popup.resize(640, 480);
            var input = popup.optionInput;
            input.setValue("Draft Group"); input.setCursorPosition(2); input.setHighlightPos(7);
            popup.setFocused(input);
            String highlighted = input.getHighlighted();
            popup.resize(320, 240);
            check(popup.controlClose.getX() == popup.panelX && popup.controlClose.getY() == popup.panelY
                    && input.getX() == popup.panelX
                    && input.getY() == popup.panelY + AbstractLayersScreen.WIDGET_AND_GAP_HEIGHT
                    && popup.optionConfirm.getX() + popup.optionConfirm.getWidth() <= popup.containerX + popup.containerWidth,
                    "input popup widgets move inside the resized background");
            check(popup.optionInput == input && input.getValue().equals("Draft Group")
                    && input.getCursorPosition() == 2 && input.getHighlighted().equals(highlighted)
                    && popup.getFocused() == input && input.isFocused(),
                    "popup resize preserves pending name, cursor, selection and focus");
            check(editor.width == 320 && editor.height == 240
                    && editor.leftList.getX() == editor.leftPanelX + AbstractLayersScreen.LIST_BORDER,
                    "popup resize also updates the parent drawn behind it");
            var popupState = new GuiRenderState();
            popup.extractRenderState(new GuiGraphicsExtractor(mc, popupState, 0, 0), 0, 0, 0);
            check(elements(popupState) > 0, "resized input popup extracts actual graphics");
            var additional = new AdditionalSettingsScreen(editor);
            mc.gui.setScreen(additional);
            additional.resize(640, 480); additional.resize(320, 240);
            check(additional.optionHotbarSelectionFix.getX() == additional.panelX
                    && additional.optionHotbarSelectionFix.getY() == additional.panelY + AbstractLayersScreen.WIDGET_AND_GAP_HEIGHT,
                    "non-input popup options also follow compact resize");
            migration(directory);
            Files.writeString(Config.file.toPath(), "{invalid layout"); Config.load();
            try (var entries = Files.list(directory)) {
                check(entries.anyMatch(path -> path.getFileName().toString().startsWith("layout.json.invalid-")), "invalid layout preserved before recovery");
            }
            check(Config.getOptions().groups.containsKey("Default"), "invalid layout recovers usable defaults");
            LoggerFactory.getLogger("TheLadsCore").info("Lads raised title probe END: {} passed, 0 failed", passed);
        } catch (Throwable failure) {
            LoggerFactory.getLogger("TheLadsCore").error("Lads native feature probe FAILED: raised layout after {} checks", passed, failure);
        } finally {
            if (mc.gui.screen() != originalScreen) mc.gui.setScreen(originalScreen);
            Config.file = originalFile; Config.setOptions(originalOptions); AbstractLayersScreen.currentGroup = originalGroup;
            module.setEnabled(enabled); module.setLastModified(modified);
        }
    }
    private static void migration(java.nio.file.Path directory) throws Exception {
        // The exact ten-layer schema saved by the previously shipped 26.2 Raised 5.1.2 jar.
        String original;
        try (var stream = NativeRaisedProbe.class.getResourceAsStream("/assets/theladscore/tests/raised-5.1.2.json")) {
            if (stream == null) throw new IllegalStateException("Missing legacy layout fixture");
            original = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        var upstreamFile = directory.resolve("raised.json");
        Files.writeString(upstreamFile, original);
        Files.copy(upstreamFile, Config.file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Config.load();
        check(Files.readString(upstreamFile).equals(original), "migration leaves upstream layout bytes unchanged");
        try (var backups = Files.list(directory)) {
            var backup = backups.filter(path -> path.getFileName().toString().startsWith("layout.json.pre-migration-")).findFirst().orElseThrow();
            check(Files.readString(backup).equals(original), "migration retains exact native pre-migration copy");
        }
        check(Translate.getY("minecraft:hotbar") == -2 && Translate.getY("minecraft:action_bar") == -2
                && Translate.getY("minecraft:chat") == 0 && !Config.getOptions().groups.containsKey("Default"),
                "actual prior layout imports the two-pixel lift without adding a second default group");
        check(Config.getOptions().layers.containsKey("minecraft:boss_bar")
                && Config.getOptions().layers.containsKey("minecraft:scoreboard")
                && Config.getOptions().layers.containsKey("minecraft:player_list")
                && Config.getOptions().layers.containsKey("minecraft:unknown"), "legacy layer names map to active native render hooks");
        String converted = Files.readString(Config.file.toPath()); Config.load();
        check(Files.readString(Config.file.toPath()).equals(converted), "migrated layout reload is idempotent");

        String links = """
                {"layers":{
                  "test:a":{"displacement":{"x":5,"y":6},"direction":{"x":"LEFT","y":"UP"},"sync":"test:b"},
                  "test:b":{"displacement":{"x":11,"y":22},"direction":{"x":"RIGHT","y":"DOWN"},"sync":"test:c"},
                  "test:c":{"displacement":{"x":33,"y":44},"direction":{"x":"NONE","y":"DOWN"},"sync":"test:c"},
                  "test:cycle_a":{"displacement":{"x":7,"y":8},"direction":{"x":"RIGHT","y":"UP"},"sync":"test:cycle_b"},
                  "test:cycle_b":{"displacement":{"x":9,"y":10},"direction":{"x":"LEFT","y":"DOWN"},"sync":"test:cycle_a"},
                  "test:missing":{"displacement":{"x":-4,"y":-6},"direction":{"x":"LEFT","y":"UP"},"sync":"absent:layer"},
                  "minecraft:bossbar":{"displacement":{"x":1,"y":2},"direction":{"x":"RIGHT","y":"DOWN"},"sync":"test:b"},
                  "minecraft:players":{"displacement":{"x":3,"y":4},"direction":{"x":"NONE","y":"UP"},"sync":"test:c"},
                  "minecraft:sidebar":{"displacement":{"x":5,"y":6},"direction":{"x":"LEFT","y":"NONE"},"sync":"test:b"},
                  "minecraft:other":{"displacement":{"x":7,"y":8},"direction":{"x":"RIGHT","y":"UP"},"sync":"test:c"}
                },"resource":{"texture":"PATCH"}}
                """;
        Files.writeString(Config.file.toPath(), links); Config.load();
        check(Translate.getX("test:a") == -11 && Translate.getY("test:a") == -22
                && Translate.getX("test:b") == 33 && Translate.getY("test:b") == 44,
                "sync chains preserve direct-target raw offsets and each caller direction");
        check(Translate.getX("test:cycle_a") == 9 && Translate.getY("test:cycle_a") == -10
                && Translate.getX("test:cycle_b") == -7 && Translate.getY("test:cycle_b") == 8,
                "cyclic links preserve non-recursive original behavior");
        check(Translate.getX("test:missing") == 4 && Translate.getY("test:missing") == 6,
                "missing sync targets retain signed self-offset fallback");
        check(Translate.getX("test:c") == 0 && Translate.getY("test:c") == 44,
                "self link and disabled axis preserve original behavior");
        check(Translate.getX("minecraft:boss_bar") == 11 && Translate.getY("minecraft:boss_bar") == 22
                && Translate.getY("minecraft:player_list") == -44 && Translate.getX("minecraft:scoreboard") == -11
                && Translate.getX("minecraft:unknown") == 33 && Translate.getY("minecraft:unknown") == -44,
                "renamed layer aliases preserve effective translations");
        check(Config.getOptions().additionalSettings.hotbarSelectionFix == HotbarSelectionFix.PATCH,
                "legacy selector texture preference survives migration");
        var shared = Config.getOptions().groups.get("Imported: test:b");
        check(shared.layers.containsAll(java.util.List.of("test:a", "minecraft:boss_bar", "minecraft:scoreboard"))
                && !shared.layers.contains("test:b"), "layers with the same direct sync source share one editable offset");
        shared.offset.setX(17); shared.offset.setY(19);
        check(Translate.getX("test:a") == -17 && Translate.getY("test:a") == -19
                && Translate.getX("minecraft:boss_bar") == 17 && Translate.getY("minecraft:boss_bar") == 19
                && Translate.getX("test:b") == 33 && Translate.getY("test:b") == 44,
                "editing a migrated group retains its intended synchronized members");
        Config.save(); String linkedRoundTrip = Config.gson.toJson(Config.getOptions()); Config.load();
        check(Config.gson.toJson(Config.getOptions()).equals(linkedRoundTrip), "migrated synchronized groups survive save and reload");
    }
    private static int selection(HotbarSelectionFix mode) {
        Config.getOptions().additionalSettings.hotbarSelectionFix = mode;
        var state = new GuiRenderState(); var graphics = new GuiGraphicsExtractor(Minecraft.getInstance(), state, 0, 0);
        NativeRaised.selection(graphics, RenderPipelines.GUI_TEXTURED, Identifier.withDefaultNamespace("hud/hotbar_selection"), 20, 20, 24, 23);
        return elements(state);
    }
    private static int elements(GuiRenderState state) { int[] count = {0}; state.forEachElement(e -> count[0]++, GuiRenderState.TraverseRange.ALL); return count[0]; }
    private static void check(boolean value, String message) { if (!value) throw new IllegalStateException(message); passed++; }
}
