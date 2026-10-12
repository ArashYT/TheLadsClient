package com.thelads.core.v1_8_9.feature;

import static com.thelads.core.v1_8_9.feature.CoreProbe.after;
import static com.thelads.core.v1_8_9.feature.CoreProbe.check;
import static com.thelads.core.v1_8_9.feature.CoreProbe.click;
import static com.thelads.core.v1_8_9.feature.CoreProbe.mouse;
import static com.thelads.core.v1_8_9.feature.CoreProbe.retry;
import static com.thelads.core.v1_8_9.feature.CoreProbe.screenshot;
import static com.thelads.core.v1_8_9.feature.CoreProbe.tap;

import com.sun.net.httpserver.HttpServer;
import com.thelads.core.client.QaSkin;
import com.thelads.core.v1_8_9.gui.SkinChangerScreen189;
import com.thelads.core.v1_8_9.gui.SkinCustomizationScreen189;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EnumPlayerModelParts;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.input.Keyboard;

/**
 * QA only (LADS_VERIFY_189_ONLY=skin): the Skin Customization and Skin changer screens, through real clicks. From the title screen:
 * Options > Skin Customization, a drag on the preview, Change skin, a skin loaded from a file (QaSkin: checkerboard outer layers), from
 * a web address (a server on this machine), a player name and a UUID, an old 64 x 32 file, the slim model, Done, the layer toggles; then in the QA world the
 * same screen from the pause menu, and the skin on the player (third person). A skin saved by an earlier run must be on again at the
 * start. Photos: lads-qa/screenshots/skin-*.png. Layer toggles and the camera are put back as found; the saved skin stays for the next run.
 */
final class Probe191Skin {
    static final List<CoreProbe.Step> TITLE = Arrays.<CoreProbe.Step>asList(Probe191Skin::start, Probe191Skin::optionsOpened, Probe191Skin::customization,
        Probe191Skin::turned, Probe191Skin::changer, Probe191Skin::classicLoad, Probe191Skin::loaded, Probe191Skin::slimModel, Probe191Skin::fromUrl, Probe191Skin::fromName,
        Probe191Skin::fromUuid, Probe191Skin::legacyFile, Probe191Skin::fromFileAgain, Probe191Skin::backToCustomization, Probe191Skin::layers3dOn, Probe191Skin::layers3dOff, Probe191Skin::layers3dBack, Probe191Skin::layersOff, Probe191Skin::layersOn, Probe191Skin::closed,
        Probe191Skin::toTitle);
    static final List<CoreProbe.Step> WORLD = Arrays.<CoreProbe.Step>asList(Probe191Skin::worldPlayer, Probe191Skin::worldPlayerShot,
        Probe191Skin::pauseOptions, Probe191Skin::pauseSkin, Probe191Skin::pauseCustomization, Probe191Skin::accountSkin,
        Probe191Skin::accountSkinBack, Probe191Skin::saved);
    private static final String[] LAYER_TOGGLES = {"Hat", "Jacket", "Left Sleeve", "Right Pants Leg"};
    private static Set<EnumPlayerModelParts> partsWere;
    private static boolean started, restarted, optionWas;
    private static int viewWas, nameWaits, boxWaits;
    private static volatile int hits;
    private static float pitchWas, gammaWas, yawBefore;
    private static File skinFile;
    private static HttpServer server;
    private static String shotPending, textureBefore;

    private Probe191Skin() {}

    private static File gameFile(Minecraft mc, String path) {
        return new File(mc.mcDataDir, path);
    }

    private static boolean start(Minecraft mc) throws Exception {
        if (CoreProbe.essentialModalOpen()) {
            tap(Keyboard.KEY_ESCAPE, (char) 27);
            return retry(5);
        }
        check(mc.currentScreen instanceof GuiMainMenu, "the run starts on the title screen");
        if (!started) {
            started = true;
            partsWere = EnumSet.copyOf(mc.gameSettings.getModelParts());
            restarted = gameFile(mc, "config/lads-skin/skin.png").isFile();
        }
        if (restarted) { // a skin kept by an earlier run is worn again once the game has started
            if (LocalSkins189.current() == null) return retry(10);
            check(LocalSkins189.current() != null, "the skin saved by the last run is on again (slim " + LocalSkins189.current().slim + ")");
        } else check(LocalSkins189.current() == null, "no skin saved yet: the account's own skin");
        press(mc, mc.currentScreen, I18n.format("menu.options"));
        return after(10);
    }

    private static boolean optionsOpened(Minecraft mc) throws Exception {
        check(mc.currentScreen instanceof GuiOptions, "Options... on the title screen opens the options");
        press(mc, mc.currentScreen, I18n.format("options.skinCustomisation"));
        return after(15);
    }

    private static boolean customization(Minecraft mc) throws Exception {
        if (!shoot(mc, "skin-1-customization-from-title")) return retry(4);
        check(mc.currentScreen instanceof SkinCustomizationScreen189, "Skin Customization... in the options opens the Lads Skin Customization screen (" + mc.currentScreen + ")");
        SkinCustomizationScreen189 screen = (SkinCustomizationScreen189) mc.currentScreen;
        for (EnumPlayerModelParts part : EnumPlayerModelParts.values())
            check(screen.button(I18n.format("options.modelPart." + part.getPartName())) != null, "a toggle for " + part.getPartName());
        check(screen.button("Change skin") != null && screen.button(I18n.format("gui.done")) != null, "Change skin and Done");
        yawBefore = screen.previewYaw();
        // Drag the preview: the mouse goes down on it, moves 20 GUI pixels right and comes up.
        int x = Math.max(12, screen.width / 2 - 180) + Math.min(150, screen.width / 3) / 2, y = 120;
        mouse(0, true, x, y);
        mouse(-1, false, x + 10, y);
        mouse(-1, false, x + 20, y);
        mouse(0, false, x + 20, y);
        return after(10);
    }

    private static boolean turned(Minecraft mc) throws Exception {
        if (!shoot(mc, "skin-2-turned")) return retry(4);
        SkinCustomizationScreen189 screen = (SkinCustomizationScreen189) mc.currentScreen;
        check(Math.abs(screen.previewYaw() - yawBefore - 50f) < 0.01f, "dragging the preview 20 pixels turned it by 50 degrees (" + yawBefore + " to " + screen.previewYaw() + ")");
        press(mc, screen, "Change skin");
        return after(15);
    }

    private static boolean changer(Minecraft mc) throws Exception {
        if (!shoot(mc, "skin-3-changer")) return retry(4);
        check(mc.currentScreen instanceof SkinChangerScreen189, "Change skin opens the Skin changer");
        SkinChangerScreen189 screen = (SkinChangerScreen189) mc.currentScreen;
        check(screen.button("Load skin") != null && screen.button("Add File") != null && screen.button("Model: ") != null
            && screen.button("Use account skin") != null && screen.button("Done") != null, "Load skin, Add File, Model, Use account skin and Done");
        return after(1);
    }

    /** Starts from the classic model (a skin kept by the last run may be slim), then loads the QA skin file. */
    private static boolean classicLoad(Minecraft mc) throws Exception {
        SkinChangerScreen189 screen = (SkinChangerScreen189) mc.currentScreen;
        if (screen.button("Model: Slim") != null) {
            press(mc, screen, "Model: Slim");
            return retry(5);
        }
        check(screen.button("Model: Classic") != null, "the Skin changer starts from the classic model");
        skinFile = gameFile(mc, "lads-qa/qa-skin.png");
        skinFile.getParentFile().mkdirs();
        Files.write(skinFile.toPath(), QaSkin.png());
        screen.source().setText(skinFile.getAbsolutePath());
        press(mc, screen, "Load skin");
        return after(5);
    }

    private static boolean loaded(Minecraft mc) throws Exception {
        SkinChangerScreen189 screen = (SkinChangerScreen189) mc.currentScreen;
        if (LocalSkins189.current() == null || !"Skin applied locally".equals(screen.status())) return retry(10);
        if (!shoot(mc, "skin-4-loaded")) return retry(4);
        check(!LocalSkins189.current().slim, "a skin file loaded: " + screen.status() + " (classic model)");
        check(gameFile(mc, "config/lads-skin/skin.png").isFile() && "wide".equals(read(mc, "config/lads-skin/model.txt")), "the skin and its model were saved in config/lads-skin");
        press(mc, screen, "Model: Classic");
        return after(10);
    }

    private static boolean slimModel(Minecraft mc) throws Exception {
        if (!shoot(mc, "skin-5-slim")) return retry(4);
        SkinChangerScreen189 screen = (SkinChangerScreen189) mc.currentScreen;
        check(LocalSkins189.current().slim && screen.button("Model: Slim") != null, "Model flipped to Slim");
        check("slim".equals(read(mc, "config/lads-skin/model.txt")), "the slim model was saved");
        // A skin from a web address: a web server on this machine serves the QA skin.
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        final byte[] png = QaSkin.png();
        server.createContext("/skin.png", exchange -> {
            hits++;
            exchange.sendResponseHeaders(200, png.length);
            exchange.getResponseBody().write(png);
            exchange.close();
        });
        server.start();
        textureBefore = LocalSkins189.current().texture.toString();
        screen.source().setText("http://127.0.0.1:" + server.getAddress().getPort() + "/skin.png");
        press(mc, screen, "Load skin");
        return after(5);
    }

    private static boolean fromUrl(Minecraft mc) throws Exception {
        SkinChangerScreen189 screen = (SkinChangerScreen189) mc.currentScreen;
        if (LocalSkins189.current() == null || LocalSkins189.current().texture.toString().equals(textureBefore) || !"Skin applied locally".equals(screen.status())) return retry(10);
        server.stop(0);
        check(hits == 1 && LocalSkins189.current().slim, "a skin URL loaded through the web server (1 request) and kept the slim model: " + screen.status());
        // A player name goes to Mojang's servers; the answer is only logged (no network in a QA run is no failure).
        screen.source().setText("Dinnerbone");
        press(mc, screen, "Load skin");
        return after(5);
    }

    private static boolean fromName(Minecraft mc) throws Exception {
        SkinChangerScreen189 screen = (SkinChangerScreen189) mc.currentScreen;
        if ("Loading skin...".equals(screen.status()) && ++nameWaits < 60) return retry(10);
        LogManager.getLogger("TheLadsCore").info("Lads 1.8.9 skin probe: the player name Dinnerbone answered: {}", screen.status());
        check(!"Loading skin...".equals(screen.status()), "a player name lookup finished: " + screen.status());
        nameWaits = 0;
        screen.source().setText("61699b2ed3274a019f1e0ea8c3f06bc6"); // the same player by UUID: no name lookup, the session server alone
        press(mc, screen, "Load skin");
        return after(5);
    }

    private static boolean fromUuid(Minecraft mc) throws Exception {
        SkinChangerScreen189 screen = (SkinChangerScreen189) mc.currentScreen;
        if ("Loading skin...".equals(screen.status()) && ++nameWaits < 60) return retry(10);
        LogManager.getLogger("TheLadsCore").info("Lads 1.8.9 skin probe: the UUID of Dinnerbone answered: {}", screen.status());
        check(!"Loading skin...".equals(screen.status()), "a UUID lookup finished: " + screen.status());
        // The old 64 x 32 layout: only the right leg has pixels; vanilla's conversion must mirror them into the left leg of a 64 x 64 texture.
        BufferedImage legacy = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 16; x++) for (int y = 16; y < 32; y++) legacy.setRGB(x, y, 0xFFFF0000);
        File file = gameFile(mc, "lads-qa/legacy-64x32.png");
        ImageIO.write(legacy, "png", file);
        textureBefore = LocalSkins189.current() == null ? "" : LocalSkins189.current().texture.toString();
        screen.source().setText(file.getAbsolutePath());
        press(mc, screen, "Load skin");
        return after(5);
    }

    private static boolean legacyFile(Minecraft mc) throws Exception {
        SkinChangerScreen189 screen = (SkinChangerScreen189) mc.currentScreen;
        if (LocalSkins189.current() == null || LocalSkins189.current().texture.toString().equals(textureBefore) || !"Skin applied locally".equals(screen.status())) return retry(10);
        int[] pixels = ((DynamicTexture) mc.getTextureManager().getTexture(LocalSkins189.current().texture)).getTextureData();
        check(pixels.length == 64 * 64 && (pixels[58 * 64 + 22] & 0xFFFFFF) == 0xFF0000, "a 64 x 32 skin file became a 64 x 64 texture with its right leg mirrored into the left");
        screen.source().setText(skinFile.getAbsolutePath());
        press(mc, screen, "Load skin");
        return after(5);
    }

    private static boolean fromFileAgain(Minecraft mc) throws Exception {
        SkinChangerScreen189 screen = (SkinChangerScreen189) mc.currentScreen;
        if (!"Skin applied locally".equals(screen.status())) return retry(10);
        check(LocalSkins189.current().slim, "the QA skin file is on again, slim");
        press(mc, screen, "Done");
        return after(15);
    }

    private static boolean backToCustomization(Minecraft mc) throws Exception {
        check(mc.currentScreen instanceof SkinCustomizationScreen189, "Done in the Skin changer goes back to Skin Customization");
        return after(2);
    }

    /** The QA skin's outer layers as 3D boxes (the 3D layers button is on), then the same screen with the button off: the flat layers. */
    private static boolean layers3dOn(Minecraft mc) throws Exception {
        SkinCustomizationScreen189 screen = (SkinCustomizationScreen189) mc.currentScreen;
        if (screen.previewBoxes() == 0 && ++boxWaits < 30) return retry(5); // the first frame reads the skin back
        if (!shoot(mc, "skin-6a-3d-layers-on")) return retry(4);
        optionWas = SkinLayers189.previewOn();
        check(optionWas && screen.button("3D layers: ON") != null, "the 3D layers button says " + screen.button("3D layers").displayString + " (the module option as shipped)");
        check(screen.previewBoxes() > 300, "the preview drew the QA skin's outer layers as " + screen.previewBoxes() + " 3D boxes");
        press(mc, screen, "3D layers");
        return after(10);
    }

    private static boolean layers3dOff(Minecraft mc) throws Exception {
        if (!shoot(mc, "skin-6b-3d-layers-off")) return retry(4);
        SkinCustomizationScreen189 screen = (SkinCustomizationScreen189) mc.currentScreen;
        check(!SkinLayers189.previewOn() && screen.button("3D layers: OFF") != null && screen.previewBoxes() == 0, "3D layers: OFF turned the module's 3D Preview off and the preview back to the flat layers");
        press(mc, screen, "3D layers");
        return after(10);
    }

    private static boolean layers3dBack(Minecraft mc) throws Exception {
        SkinCustomizationScreen189 screen = (SkinCustomizationScreen189) mc.currentScreen;
        check(SkinLayers189.previewOn() == optionWas && screen.button("3D layers: ON") != null, "3D layers: ON again");
        return after(2);
    }

    private static boolean layersOff(Minecraft mc) throws Exception {
        SkinCustomizationScreen189 screen = (SkinCustomizationScreen189) mc.currentScreen;
        for (String name : LAYER_TOGGLES) press(mc, screen, name);
        return after(10);
    }

    private static boolean layersOn(Minecraft mc) throws Exception {
        if (!shoot(mc, "skin-6-layers-off")) return retry(4);
        SkinCustomizationScreen189 screen = (SkinCustomizationScreen189) mc.currentScreen;
        Set<EnumPlayerModelParts> parts = mc.gameSettings.getModelParts();
        check(!parts.contains(EnumPlayerModelParts.HAT) && !parts.contains(EnumPlayerModelParts.JACKET) && !parts.contains(EnumPlayerModelParts.LEFT_SLEEVE)
            && !parts.contains(EnumPlayerModelParts.RIGHT_PANTS_LEG) && parts.contains(EnumPlayerModelParts.RIGHT_SLEEVE), "the four toggles turned their layers off and no more");
        check(screen.button("Hat").displayString.endsWith(I18n.format("options.off")), "the Hat toggle says " + screen.button("Hat").displayString);
        for (String name : LAYER_TOGGLES) press(mc, screen, name);
        return after(10);
    }

    private static boolean closed(Minecraft mc) throws Exception {
        if (!shoot(mc, "skin-7-layers-on")) return retry(4);
        SkinCustomizationScreen189 screen = (SkinCustomizationScreen189) mc.currentScreen;
        check(mc.gameSettings.getModelParts().equals(partsWere), "the toggles turned the layers back on");
        press(mc, screen, I18n.format("gui.done"));
        return after(10);
    }

    private static boolean toTitle(Minecraft mc) throws Exception {
        check(mc.currentScreen instanceof GuiOptions, "Done in Skin Customization goes back to the options");
        press(mc, mc.currentScreen, I18n.format("gui.done"));
        return after(10);
    }

    // The world part: the saved skin is on the player, and the pause menu's Options reach the same screen.

    private static boolean worldPlayer(Minecraft mc) throws Exception {
        check(mc.currentScreen == null && LocalSkins189.current() != null, "in the QA world with the skin from the title screen");
        AbstractClientPlayer player = mc.thePlayer;
        check(player.getLocationSkin().equals(LocalSkins189.current().texture) && "slim".equals(player.getSkinType()) && player.hasSkin(),
            "the player wears the local skin with the slim model: " + player.getLocationSkin());
        viewWas = mc.gameSettings.thirdPersonView;
        pitchWas = mc.thePlayer.rotationPitch;
        gammaWas = mc.gameSettings.gammaSetting;
        mc.gameSettings.thirdPersonView = 2; // the player seen from the front
        mc.gameSettings.gammaSetting = 100;
        mc.thePlayer.rotationPitch = mc.thePlayer.prevRotationPitch = 0;
        return after(40);
    }

    private static boolean worldPlayerShot(Minecraft mc) throws Exception {
        if (!shoot(mc, "skin-8-world-player-slim")) return retry(4);
        mc.gameSettings.thirdPersonView = viewWas;
        mc.gameSettings.gammaSetting = gammaWas;
        mc.thePlayer.rotationPitch = mc.thePlayer.prevRotationPitch = pitchWas;
        tap(Keyboard.KEY_ESCAPE, (char) 27);
        return after(10);
    }

    private static boolean pauseOptions(Minecraft mc) throws Exception {
        check(mc.currentScreen instanceof GuiIngameMenu, "Escape in the QA world opens the pause menu");
        press(mc, mc.currentScreen, I18n.format("menu.options"));
        return after(10);
    }

    private static boolean pauseSkin(Minecraft mc) throws Exception {
        check(mc.currentScreen instanceof GuiOptions, "Options... on the pause menu opens the options");
        press(mc, mc.currentScreen, I18n.format("options.skinCustomisation"));
        return after(20);
    }

    private static boolean pauseCustomization(Minecraft mc) throws Exception {
        if (!shoot(mc, "skin-9-customization-from-pause")) return retry(4);
        check(mc.currentScreen instanceof SkinCustomizationScreen189, "Skin Customization... in the options of the pause menu opens the Lads screen too");
        press(mc, mc.currentScreen, "Change skin");
        return after(15);
    }

    private static boolean accountSkin(Minecraft mc) throws Exception {
        check(mc.currentScreen instanceof SkinChangerScreen189, "Change skin opens the Skin changer in the world too");
        press(mc, mc.currentScreen, "Use account skin");
        return after(10);
    }

    private static boolean accountSkinBack(Minecraft mc) throws Exception {
        if (!shoot(mc, "skin-10-account-skin")) return retry(4);
        check(LocalSkins189.current() == null && !mc.thePlayer.getLocationSkin().toString().startsWith("theladscore:local_skin/")
            && !gameFile(mc, "config/lads-skin/skin.png").exists(),
            "Use account skin: the player wears its own skin again (" + mc.thePlayer.getLocationSkin() + ") and the saved skin is gone");
        // The skin goes on again, so that the next run finds it saved.
        ((SkinChangerScreen189) mc.currentScreen).source().setText(skinFile.getAbsolutePath());
        press(mc, mc.currentScreen, "Load skin");
        return after(10);
    }

    private static boolean saved(Minecraft mc) throws Exception {
        if (LocalSkins189.current() == null) return retry(10);
        check(gameFile(mc, "config/lads-skin/skin.png").isFile(), "the skin is saved again for the next run");
        mc.displayGuiScreen(null);
        stop();
        return after(5);
    }

    /** The layer toggles back as found. */
    static void stop() {
        if (!started) return;
        started = false;
        if (server != null) server.stop(0);
        Minecraft mc = Minecraft.getMinecraft();
        if (partsWere != null) for (EnumPlayerModelParts part : EnumPlayerModelParts.values()) mc.gameSettings.setModelPartEnabled(part, partsWere.contains(part));
    }

    /**
     * A photo of the screen as it is now: false while Essential's preview of the last photo is on the screen (it would sit over this
     * one, top right) and for the first call after, while a few frames draw; true once the photo is taken.
     */
    private static boolean shoot(Minecraft mc, String name) throws Exception {
        if (CoreProbe.previewsShowing()) return false;
        if (!name.equals(shotPending)) {
            shotPending = name;
            mc.guiAchievement.clearAchievements(); // the "Press E" hint
            return false;
        }
        shotPending = null;
        screenshot(mc, name);
        return true;
    }

    private static String read(Minecraft mc, String path) throws Exception {
        return new String(Files.readAllBytes(gameFile(mc, path).toPath()), "UTF-8").trim();
    }

    /** A real click on the screen's button with that label. */
    private static void press(Minecraft mc, GuiScreen screen, String label) {
        List<GuiButton> buttons = ReflectionHelper.getPrivateValue(GuiScreen.class, screen, "buttonList", "field_146292_n");
        for (GuiButton button : buttons)
            if (button.displayString.startsWith(label)) {
                try {
                    click(button.xPosition + button.width / 2, button.yPosition + button.height / 2);
                } catch (Exception failure) {
                    throw new IllegalStateException(failure);
                }
                LogManager.getLogger("TheLadsCore").info("Lads 1.8.9 skin probe: clicked '{}'", button.displayString);
                return;
            }
        throw new IllegalStateException("1.8.9 core QA: no button '" + label + "' on " + screen);
    }
}
