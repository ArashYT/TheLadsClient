package com.thelads.core.v1_8_9.gui;

import com.thelads.core.client.LadsNetwork;
import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.title.TitleScreenTheme;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.v1_8_9.adapter.GuiLadsAdapter;
import com.thelads.core.v1_8_9.cosmetics.anim.CosmeticsScreen189;
import com.thelads.core.v1_8_9.feature.Screenshots189;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiButtonLanguage;
import net.minecraft.client.gui.GuiLanguage;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.LogManager;

/**
 * The Lads title screen on 1.8.9 (TitleScreenMixin on the other versions), through Forge's screen events so
 * OptiFine's GuiMainMenu changes keep working: themed artwork, Singleplayer, Multiplayer, Lads Mods, Forge's Mods, Options,
 * Screenshots, Replays, More, Quit, and Switch Versions as Lads buttons; Switch (accounts) beside the
 * account name, and language + fullscreen toggles top right. Virtual scaling ensures UI parity
 * matching GUI scale 3.0 across all resolutions.
 */
public final class LadsTitleScreen189 {
    public static final LadsTitleScreen189 INSTANCE = new LadsTitleScreen189();
    private static final int LADS_ID = 0x4C414453 + 1; // the pause menu's Lads button is 0x4C414453; vanilla and Forge use 0-14
    private GuiMainMenu screen;
    private List<GuiButton> buttons;
    private GuiButton ladsButton, cosmeticsButton, friendsButton, moreButton, screenshotsButton, switchVersionButton, replaysButton, networkButton;
    private CompactButton189 switchButton, fullscreenButton, languageButton;
    private final List<GuiButton> seen = new ArrayList<>(), main = new ArrayList<>(), extras = new ArrayList<>();
    private long initNanos;
    private TitleScreenTheme.Layout layout;
    private int layoutWidth, layoutHeight;
    private long lastFrame;
    private double seconds;
    private boolean logged;

    private LadsTitleScreen189() {}

    /** QA: the title screen the Lads layout is on (null: vanilla), and its main buttons as laid out. */
    public GuiMainMenu screen() { return screen; }
    public List<GuiButton> mainButtons() { return main; }
    public GuiButton cosmeticsButton() { return cosmeticsButton; }
    public GuiButton friendsButton() { return friendsButton; }
    public static String friendsButtonLabel() {
        int online = com.thelads.core.v1_8_9.social.SocialBridge189.onlineFriendsCount();
        return online > 0 ? "Friends (" + online + ")" : "Friends";
    }
    public GuiButton moreButton() { return moreButton; }
    public GuiButton switchVersionButton() { return switchVersionButton; }
    public GuiButton switchButton() { return switchButton; }
    public GuiButton fullscreenButton() { return fullscreenButton; }
    /** QA: every button the Lads layout places (main, Switch, fullscreen, language). */
    public List<GuiButton> placedButtons() {
        List<GuiButton> placed = new ArrayList<>(main);
        placed.add(switchButton);
        placed.add(fullscreenButton);
        placed.add(languageButton);
        return placed;
    }

    /**
     * Window pixels per layout pixel: 3 (GUI scale 3 everywhere), less on windows under 720x540, so the layout never gets
     * below its 240x180 minimum (TitleScreenTheme.layout), which would put its bottom (the account card) off the screen.
     */
    private static double windowPerLayoutPixel(Minecraft mc) {
        return Math.min(3.0, Math.min(mc.displayWidth / 240.0, mc.displayHeight / 180.0));
    }

    private int targetWidth() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.displayWidth > 0 && mc.displayHeight > 0) {
            return (int) Math.round(mc.displayWidth / windowPerLayoutPixel(mc));
        }
        return screen != null ? screen.width : 320;
    }

    private int targetHeight() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.displayWidth > 0 && mc.displayHeight > 0) {
            return (int) Math.round(mc.displayHeight / windowPerLayoutPixel(mc));
        }
        return screen != null ? screen.height : 240;
    }

    private float matrixScale() {
        int targetH = targetHeight();
        return (float) (screen != null ? screen.height : 240) / (float) Math.max(1, targetH);
    }

    @SubscribeEvent
    public void init(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!(event.gui instanceof GuiMainMenu)) return;
        screen = null;
        layout = null;
        Module module = ModuleManager.getInstance().getModule("TitleScreen");
        if (module == null || !module.isEnabled()) return;
        screen = (GuiMainMenu) event.gui;
        buttons = event.buttonList;
        extras.clear();
        initNanos = System.nanoTime();

        buttons.add(ladsButton = new GuiButton(LADS_ID, 0, 0, 1, 1, "Lads Mods"));
        buttons.add(cosmeticsButton = new GuiButton(LADS_ID + 10, 0, 0, 1, 1, "Cosmetics"));
        buttons.add(friendsButton = new GuiButton(LADS_ID + 12, 0, 0, 1, 1, friendsButtonLabel()));
        buttons.add(moreButton = new GuiButton(LADS_ID + 2, 0, 0, 1, 1, "More..."));
        screenshotsButton = Screenshots189.active() ? new GuiButton(LADS_ID + 4, 0, 0, 1, 1, "Screenshots") : null;
        if (screenshotsButton != null) buttons.add(screenshotsButton);

        buttons.add(switchVersionButton = new GuiButton(LADS_ID + 6, 0, 0, 1, 1, "Switch Versions"));
        replaysButton = Loader.isModLoaded("replaymod") ? new GuiButton(LADS_ID + 8, 0, 0, 1, 1, "Replays") : null;
        if (replaysButton != null) buttons.add(replaysButton);
        networkButton = LadsNetwork.titleButton() ? new GuiButton(LADS_ID + 14, 0, 0, 1, 1, LadsNetwork.TITLE_LABEL) : null;
        if (networkButton != null) buttons.add(networkButton);

        // Beside the account name and top right
        GuiMainMenu title = screen;
        buttons.add(switchButton = new CompactButton189(LADS_ID + 1, 0, 0, 1, 1, TitleScreenTheme.SWITCH, () -> "switch",
            () -> Minecraft.getMinecraft().displayGuiScreen(new AccountSwitcherScreen189(title))));
        buttons.add(fullscreenButton = CompactButton189.fullscreen(LADS_ID + 3, screen.width));
        buttons.add(languageButton = new CompactButton189(LADS_ID + 5, screen.width - 50, 6, 20, 20, "Language", () -> "globe",
            () -> Minecraft.getMinecraft().displayGuiScreen(new GuiLanguage(title, Minecraft.getMinecraft().gameSettings, Minecraft.getMinecraft().getLanguageManager()))));

        lastFrame = 0;
    }

    /** Lays the buttons out on the first frame, and again when they change. */
    private void ensureLayout() {
        int targetW = targetWidth();
        int targetH = targetHeight();
        float ms = matrixScale();

        if (layout != null && layoutWidth == targetW && layoutHeight == targetH && seen.equals(buttons)) return;
        main.clear();
        for (GuiButton button : new ArrayList<>(buttons)) {
            if (button == switchButton || button == fullscreenButton || button == languageButton) continue;
            if (button == ladsButton || button == cosmeticsButton || button == friendsButton || button == moreButton || button == screenshotsButton
                || button == switchVersionButton || button == replaysButton || button == networkButton
                || vanilla(button, 1, 11, 2, 0, 4, 6)) main.add(button);
            else {
                buttons.remove(button);
                button.visible = false;
                if (button.getClass().getName().startsWith("gg.essential.")) {
                    // Ignore Essential buttons if installed
                } else if (!extras.contains(button)) extras.add(button);
            }
        }
        main.sort(Comparator.comparingInt(this::order));
        layout = TitleScreenTheme.layout(targetW, targetH, main.size(), false);
        layoutWidth = targetW;
        layoutHeight = targetH;

        for (int i = 0; i < main.size(); i++) {
            GuiButton button = main.get(i);
            TitleScreenTheme.Rect rect = layout.buttons().get(i);
            place(button, rect.x(), rect.y(), rect.width(), rect.height(), ms);
            button.visible = true;
        }

        int fullscreenWidth = CompactButton189.fullscreenWidth(targetW);
        place(fullscreenButton, TitleScreenTheme.fullscreenX(targetW, fullscreenWidth), 6, fullscreenWidth, 20, ms);
        place(languageButton, TitleScreenTheme.languageX(targetW, fullscreenWidth), 6, 20, 20, ms);

        seen.clear();
        seen.addAll(buttons);
        if (!logged) {
            logged = true;
            LogManager.getLogger("TheLadsCore-1.8.9").info("custom title initialized for Minecraft 1.8.9: {} native widgets, {} more", main.size(), extras.size());
            com.thelads.core.v1_8_9.TheLadsCore189.wipeLaunchClassLoader();
        }
    }

    private int order(GuiButton button) {
        if (button == networkButton) return 1; // after Multiplayer: the stable sort keeps the vanilla button (added first) ahead
        if (button == ladsButton) return 2;
        if (button == cosmeticsButton) return 4;
        if (button == friendsButton) return 5;
        if (button == replaysButton) return 8;
        if (button == moreButton) return 9;
        if (button.id == 4) return 10; // Quit Game
        if (button == switchVersionButton) return 11;
        switch (button.id) {
            case 1: case 11: return 0;
            case 2: return 1;
            case 6: return 3; // Forge's Mods
            case 0: return 5;
            default: return button == screenshotsButton ? 6 : 11;
        }
    }

    private static boolean vanilla(GuiButton button, int... ids) {
        if (button.getClass() != GuiButton.class && !(button instanceof GuiButtonLanguage)) return false;
        for (int id : ids) if (button.id == id) return true;
        return false;
    }

    private static String icon(GuiButton button) {
        if (button.id == LADS_ID) return "lads";
        if (button.id == LADS_ID + 2) return "dots";
        if (button.id == LADS_ID + 4) return "camera";
        if (button.id == LADS_ID + 6) return "switch";
        if (button.id == LADS_ID + 8) return "replay";
        if (button.id == LADS_ID + 10) return "wardrobe";
        if (button.id == LADS_ID + 12) return "friends";
        if (button.id == LADS_ID + 14) return "server";
        switch (button.id) {
            case 1: case 11: return "play";
            case 2: return "server";
            case 6: return "mods";
            case 0: return "settings";
            case 4: return "quit";
            default: return "more";
        }
    }

    private static String label(GuiButton button) {
        if (button.id == LADS_ID + 12) return friendsButtonLabel();
        if (vanilla(button, 14)) return "Realms";
        if (button instanceof GuiButtonLanguage) return "Language";
        String language = Minecraft.getMinecraft().getLanguageManager().getCurrentLanguage().getLanguageCode();
        if (vanilla(button, 0) && language != null && language.startsWith("en_")) return "Options";
        return button.displayString;
    }

    @SubscribeEvent
    public void layout(GuiScreenEvent.DrawScreenEvent.Pre event) {
        if (screen != null && event.gui == screen) ensureLayout();
    }

    @SubscribeEvent
    public void draw(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (screen == null || event.gui != screen || layout == null) return;
        long now = System.nanoTime();
        float elapsed = lastFrame == 0 ? 0 : (float) Math.min(0.1, (now - lastFrame) / 1e9);
        lastFrame = now;
        seconds += elapsed;
        Minecraft mc = Minecraft.getMinecraft();

        int targetW = targetWidth();
        int targetH = targetHeight();
        float ms = matrixScale();

        GlStateManager.pushMatrix();
        GlStateManager.scale(ms, ms, 1.0f);

        GuiLadsAdapter g = new GuiLadsAdapter(mc.fontRendererObj, targetW, targetH);
        TitleScreenTheme.Rect switchBox = TitleScreenTheme.renderBackground(g, layout, mc.getSession().getUsername(),
            "1.8.9" + (mc.isDemo() ? " Demo" : ""), false, seconds, true);

        int scaledMouseX = (int) Math.round(event.mouseX / ms);
        int scaledMouseY = (int) Math.round(event.mouseY / ms);

        for (int i = 0; i < main.size(); i++) {
            GuiButton button = main.get(i);
            TitleScreenTheme.Rect rect = layout.buttons().get(i);
            TitleExtrasScreen189.drawButtonAt(g, button, rect.x(), rect.y(), rect.width(), rect.height(),
                label(button), icon(button), vanilla(button, 1), scaledMouseX, scaledMouseY, elapsed);
        }

        GlStateManager.popMatrix();

        if (switchBox != null) place(switchButton, switchBox.x(), switchBox.y(), switchBox.width(), switchBox.height(), ms);

        switchButton.drawButton(mc, event.mouseX, event.mouseY, ms);
        fullscreenButton.drawButton(mc, event.mouseX, event.mouseY, ms);
        if (languageButton != null) languageButton.drawButton(mc, event.mouseX, event.mouseY, ms);
    }

    /** Layout pixels to screen pixels; edges rounded, so neighbours that touch in the layout never overlap on the screen. */
    private static void place(GuiButton button, int x, int y, int width, int height, float ms) {
        button.xPosition = Math.round(x * ms);
        button.yPosition = Math.round(y * ms);
        button.width = Math.round((x + width) * ms) - button.xPosition;
        button.height = Math.round((y + height) * ms) - button.yPosition;
    }

    private static void openReplays(GuiMainMenu parent) {
        try {
            Class<?> cls = Class.forName("com.replaymod.replay.gui.GuiReplayViewer");
            Object scr = cls.getConstructor(net.minecraft.client.gui.GuiScreen.class).newInstance(parent);
            Minecraft.getMinecraft().displayGuiScreen((net.minecraft.client.gui.GuiScreen) scr);
            return;
        } catch (Throwable ignored) {}
    }

    @SubscribeEvent
    public void action(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (screen == null || event.gui != screen) return;
        Minecraft mc = Minecraft.getMinecraft();
        boolean clicked = mc.currentScreen == screen;
        if (event.button == ladsButton) mc.displayGuiScreen(new LadsSettingsScreen189(screen));
        else if (event.button == cosmeticsButton) mc.displayGuiScreen(new CosmeticsScreen189(screen));
        else if (event.button == friendsButton) com.thelads.core.client.social.SocialBridge.get().openFriendsScreen();
        else if (event.button == screenshotsButton) Screenshots189.open(screen);
        else if (event.button == moreButton) mc.displayGuiScreen(new TitleExtrasScreen189(screen, TitleExtrasScreen189.of(screen, extras, LadsTitleScreen189::label)));
        else if (event.button == switchVersionButton) mc.displayGuiScreen(new VersionSwitchScreen189(screen));
        else if (event.button == replaysButton) openReplays(screen);
        else if (event.button == networkButton)
            mc.displayGuiScreen(new GuiConnecting(new GuiMultiplayer(screen), mc, new ServerData(LadsNetwork.NAME, LadsNetwork.ADDRESS, false)));
        else if (event.button.id == 4) mc.displayGuiScreen(new QuitConfirmScreen189(screen));
        else if (event.button == switchButton || event.button == fullscreenButton || event.button == languageButton) ((CompactButton189) event.button).press();
        else return;
        if (clicked) event.button.playPressSound(mc.getSoundHandler());
        event.setCanceled(true);
    }
}
