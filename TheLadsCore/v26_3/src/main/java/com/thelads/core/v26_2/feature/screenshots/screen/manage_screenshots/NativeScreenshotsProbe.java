package com.thelads.core.v26_2.feature.screenshots.screen.manage_screenshots;

import com.thelads.core.v26_2.feature.NativeQualityOfLife;
import com.thelads.core.v26_2.feature.screenshots.*;
import com.thelads.core.v26_2.feature.screenshots.config.*;
import com.thelads.core.v26_2.feature.screenshots.screen.ScreenshotViewerConfigScreen;
import io.github.lgatodu47.catconfig.ConfigOption;
import io.github.lgatodu47.catconfigmc.MinecraftConfigSides;
import io.github.lgatodu47.catconfigmc.OldEditBox;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.LoggerFactory;

/** Opt-in real screen/GPU probe; filesystem actions only touch generated fixtures. OS open/copy are mocked. */
public final class NativeScreenshotsProbe {
    private static int state, frames, passed;
    private static Screen parent, originalScreen;
    private static boolean captured;
    private static ManageScreenshotsScreen gallery;
    private static ScreenshotList list;
    private static Path fixtures, png, jpeg;
    private static final Map<ConfigOption<?>, Object> saved = new LinkedHashMap<>();
    private static boolean enabled;
    private static long modified;
    private static int width, height;
    public static void tick() {
        if (state < 0 || !Boolean.getBoolean("thelads.verifyIntegrations") || !ScreenshotViewer.available()) return;
        Minecraft client = Minecraft.getInstance();
        try {
            if (state == 0) {
                if (client.level == null || client.gui.screen() != null) { frames = 0; return; }
                if (++frames < 320) return;
                begin(client); state = 1; frames = 0;
            } else if (state == 1) {
                if (++frames > 200) throw new IllegalStateException("Screenshot GPU loading did not complete");
                extract(gallery);
                ScreenshotWidget first = (ScreenshotWidget) list.findByFileName(png.toFile()).orElseThrow();
                ScreenshotWidget second = (ScreenshotWidget) list.findByFileName(jpeg.toFile()).orElseThrow();
                if (first.image() == null || second.image() == null || !ScreenshotViewer.getInstance().getThumbnailManager().getThumbnail(png.toFile()).orElseThrow().isDone()) return;
                check(first.image().getWidth() == 64 && first.image().getHeight() == 36, "real full PNG decode dimensions");
                check(first.image().getPixel(3, 3) == 0xffaabbcc, "real PNG pixel survives native image conversion");
                check(second.image().getWidth() == 64, "JPEG accepted without global PNG bypass");
                var id = first.textureId(); check(id != null && client.getTextureManager().getTexture(id) != null, "actual GPU dynamic texture registered");
                complete(client, first, second);
                LoggerFactory.getLogger("TheLadsCore").info("Lads native screenshots probe END: {} checks passed, 0 failed; real gallery/GPU and isolated files, OS open/copy mocked", passed);
                restore(client); state = -1;
            }
        } catch (Throwable failure) {
            LoggerFactory.getLogger("TheLadsCore").error("Lads native screenshots probe FAILED after {} checks", passed, failure);
            restore(client); state = -1;
        }
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void begin(Minecraft client) throws Exception {
        var game = FabricLoader.getInstance().getGameDir().toRealPath();
        check(game.getParent().getFileName().toString().equals("verification") && game.getParent().getParent().getFileName().toString().equals("artifacts"), "isolated QA directory");
        originalScreen = client.gui.screen();
        parent = new PauseScreen(true); client.gui.setScreen(parent); width = parent.width; height = parent.height;
        var module = NativeQualityOfLife.module("BetterScreenshots"); enabled = module.isEnabled(); modified = module.getLastModified(); captured = true; module.setEnabled(true);
        var config = ScreenshotViewer.getInstance().getConfig();
        for (var option : ScreenshotViewerOptions.OPTIONS.options(MinecraftConfigSides.CLIENT)) saved.put(option, config.get((ConfigOption)option).orElse(null));
        Files.createDirectories(game.resolve("screenshots"));
        fixtures = Files.createTempDirectory(game.resolve("screenshots"), "screenshots-native-"); png = fixtures.resolve("a.PNG"); jpeg = fixtures.resolve("b.jpeg");
        LoggerFactory.getLogger("TheLadsCore").info("Lads native screenshots fixture directory: {} (generated test files only)", fixtures);
        BufferedImage image = new BufferedImage(64, 36, BufferedImage.TYPE_INT_RGB); image.setRGB(3, 3, 0xffaabbcc);
        ImageIO.write(image, "png", png.toFile()); ImageIO.write(image, "jpg", jpeg.toFile()); image.flush();
        Files.writeString(fixtures.resolve("ignored.txt"), "not an image"); Files.writeString(fixtures.resolve("broken.png"), "broken image fixture");
        config.put(ScreenshotViewerOptions.SCREENSHOTS_FOLDER, fixtures.toFile());
        config.put(ScreenshotViewerOptions.THUMBNAIL_FOLDER, fixtures.toFile());
        config.put(ScreenshotViewerOptions.COMPRESSION_RATIO, CompressionRatio.QUARTER);
        config.put(ScreenshotViewerOptions.PROMPT_WHEN_DELETING_SCREENSHOT, true);
        config.put(ScreenshotViewerOptions.DEFAULT_LIST_ORDER, ScreenshotListOrder.ASCENDING);
        config.put(ScreenshotViewerOptions.INITIAL_SCREENSHOT_AMOUNT_PER_ROW, 4);
        check(ScreenshotViewer.getInstance().getOpenScreenshotsScreenKey().getDefaultKey().getValue() == com.mojang.blaze3d.platform.InputConstants.KEY_F10, "default F10 key mapping");
        var keyPress = net.minecraft.client.KeyboardHandler.class.getDeclaredMethod("keyPress", long.class, int.class, KeyEvent.class);
        keyPress.setAccessible(true);
        keyPress.invoke(client.keyboardHandler, client.getWindow().handle(), 1, new KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_F10, 68, 0));
        check(client.gui.screen() instanceof ManageScreenshotsScreen, "synthetic F10 through actual keyboard handler opens gallery from pause");
        gallery = (ManageScreenshotsScreen)client.gui.screen(); list = field(gallery, "list");
        check(list.size() == 3, "PNG/JPEG and malformed PNG listed, text file excluded");
        check(list.getScreenshot(0).getScreenshotFile().getName().equals("a.PNG"), "ascending screenshot order");
        list.invertOrder(); check(list.getScreenshot(0).getScreenshotFile().getName().equals("broken.png"), "descending order action"); list.invertOrder();
        list.updateScreenshotsPerRow(1); check(list.screenshotsPerRow() == 5, "grid zoom step");
        config.put(ScreenshotViewerOptions.INVERT_ZOOM_DIRECTION, true); list.onConfigUpdate(); list.updateScreenshotsPerRow(1);
        check(list.screenshotsPerRow() == 3, "configured inverse grid zoom");
        config.put(ScreenshotViewerOptions.INVERT_ZOOM_DIRECTION, false); list.onConfigUpdate();
        gallery.resize(640, 360); check(list.size() == 3, "resize retains gallery inventory");
        check(extract(gallery) > 0, "native gallery emits render geometry");
    }
    private static void complete(Minecraft client, ScreenshotWidget first, ScreenshotWidget second) throws Exception {
        var config = ScreenshotViewer.getInstance().getConfig();
        var cached = ScreenshotViewer.getInstance().getThumbnailManager().getThumbnail(png.toFile()).orElseThrow().getNow(null);
        check(cached != null && cached.toPath().startsWith(fixtures.resolve("lads-cache-v1")), "cache stored only under reserved directory even when source/cache folders equal");
        try (var nativeThumb = ScreenshotViewerUtils.readNative(cached, false)) { check(nativeThumb.getWidth() == 16 && nativeThumb.getHeight() == 9, "quarter compression dimensions"); }
        check(ScreenshotFileIO.read(png).getWidth() == 64, "thumbnail operation preserves original");
        check(ScreenshotViewer.getInstance().getThumbnailManager().getThumbnail(png.toFile()).orElseThrow().isDone(), "cached thumbnail reused");
        gallery.enlargeScreenshot(first); check(gallery.isShowing(first), "full viewer selects requested image"); check(extract(gallery) > 0, "enlarged viewer produces actual render geometry");
        gallery.keyPressed(new KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_RIGHT, 0, 0)); check(gallery.isShowing(second), "next image keyboard navigation");
        gallery.keyPressed(new KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_LEFT, 0, 0)); check(gallery.isShowing(first), "previous image keyboard navigation");
        gallery.keyPressed(new KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE, 0, 0)); check(client.gui.screen() == gallery && !gallery.isShowing(first), "Escape closes viewer while retaining gallery");
        second.renameFile(); RenameScreenshotScreen rename = field(gallery, "dialogScreen");
        var edit = rename.children().stream().filter(OldEditBox.class::isInstance).map(OldEditBox.class::cast).findFirst().orElseThrow();
        edit.setValue("renamed"); rename.resize(700, 400);
        var resizedEdit = rename.children().stream().filter(OldEditBox.class::isInstance).map(OldEditBox.class::cast).findFirst().orElseThrow();
        check(resizedEdit.getValue().equals("renamed"), "rename draft survives resizing");
        rename.keyPressed(new KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_RETURN, 0, 0));
        check(second.getScreenshotFile().getName().equals("renamed.jpeg") && !Files.exists(jpeg), "actual rename dialog preserves source format and moves file");
        second.requestFileDeletion(); Screen confirmation = field(gallery, "dialogScreen");
        check(confirmation instanceof ConfirmDeletionScreen && Files.exists(second.getScreenshotFile().toPath()), "default delete opens confirmation without touching file");
        var buttons = confirmation.children().stream().filter(Button.class::isInstance).map(Button.class::cast).toList();
        buttons.getLast().onPress(new KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_RETURN, 0, 0));
        check(Files.exists(second.getScreenshotFile().toPath()), "cancel delete preserves screenshot");
        config.put(ScreenshotViewerOptions.PROMPT_WHEN_DELETING_SCREENSHOT, false); second.onConfigUpdate(); second.requestFileDeletion();
        check(!Files.exists(second.getScreenshotFile().toPath()) && list.size() == 2, "explicit no-prompt setting deletes selected generated file and removes row");
        int[] actions = new int[4];
        ScreenshotImageHolder mock = new ScreenshotImageHolder() {
            public File getScreenshotFile() { return png.toFile(); }
            public void openFile() { actions[0]++; }
            public void copyScreenshot() { actions[1]++; }
            public void requestFileDeletion() { actions[2]++; }
            public void renameFile() { actions[3]++; }
            public int indexInList() { return 0; }
            public Identifier textureId() { return null; }
            public com.mojang.blaze3d.platform.NativeImage image() { return null; }
        };
        var menu = new ScreenshotPropertiesMenu(() -> client);
        for (int i = 0; i < 4; i++) {
            menu.show(10,10,640,360,mock);
            ((Button) menu.children().get(i)).onPress(new KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_RETURN,0,0));
            check(actions[i] == 1, "real properties action dispatch " + i + " (OS side effect mocked)");
        }
        var settings = new ScreenshotViewerConfigScreen(gallery); settings.init(640,360); check(extract(settings) > 0, "complete configuration editor opens and renders");
        check(ScreenshotViewerOptions.OPTIONS.options(MinecraftConfigSides.CLIENT).size() == 20, "all pinned gallery configuration options retained");
        check(client.getResourceManager().getResource(Identifier.fromNamespaceAndPath("lads_screenshots", "textures/gui/sprites/widget/icons/copy.png")).isPresent(), "native icon assets packaged");
        first.close(); Object loader = field(first,"screenshotImage"); check(field(loader,"textureId") == null && field(loader,"image") == null, "closing image releases native texture and future ownership");
        gallery.onClose(); check(client.gui.screen() == parent, "gallery restores actual parent pause");
        var module = NativeQualityOfLife.module("BetterScreenshots"); module.setEnabled(false);
        check(!ScreenshotViewer.open(parent) && client.gui.screen() == parent, "module off prevents native entry");
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static void restore(Minecraft client) {
        try {
            if (captured && ScreenshotViewer.available()) {
                var config = ScreenshotViewer.getInstance().getConfig(); saved.forEach((option,value) -> config.put((ConfigOption) option,value));
                ScreenshotViewer.getInstance().getThumbnailManager().configUpdated();
                var module = NativeQualityOfLife.module("BetterScreenshots"); module.setEnabled(enabled); module.setLastModified(modified);
            }
            if (parent != null) client.gui.setScreen(originalScreen);
        } catch (Throwable failure) { LoggerFactory.getLogger("TheLadsCore").error("Lads screenshot probe restore failed",failure); }
    }
    private static int extract(Screen screen) {
        var state = new GuiRenderState(); screen.extractRenderState(new GuiGraphicsExtractor(Minecraft.getInstance(),state,0,0),0,0,0);
        int[] count = {0}; state.forEachElement(e -> count[0]++,GuiRenderState.TraverseRange.ALL); state.forEachText(e -> count[0]++); return count[0];
    }
    @SuppressWarnings("unchecked") private static <T> T field(Object object, String name) throws Exception {
        var field = object.getClass().getDeclaredField(name); field.setAccessible(true); return (T)field.get(object);
    }
    private static void check(boolean value,String description) { if (!value) throw new IllegalStateException(description); passed++; }
}
