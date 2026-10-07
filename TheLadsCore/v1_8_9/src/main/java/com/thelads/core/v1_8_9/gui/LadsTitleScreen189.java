package com.thelads.core.v1_8_9.gui;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.title.TitleScreenTheme;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.v1_8_9.adapter.GuiLadsAdapter;
import com.thelads.core.v1_8_9.feature.Screenshots189;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiButtonLanguage;
import net.minecraft.client.gui.GuiLanguage;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.LogManager;

/**
 * The Lads title screen on 1.8.9 (TitleScreenMixin on the other versions), through Forge's screen events so Essential's and
 * OptiFine's GuiMainMenu changes keep working: themed artwork, Singleplayer, Multiplayer, Lads Mods, Forge's Mods, Options,
 * Screenshots, Replays, Friends, More, Quit, and Switch Versions as Lads buttons; Essential's actions in a row above the
 * account name, Switch (accounts) beside it, and language + fullscreen toggles top right. Virtual scaling ensures UI parity
 * matching GUI scale 3.0 across all resolutions.
 */
public final class LadsTitleScreen189 {
    public static final LadsTitleScreen189 INSTANCE = new LadsTitleScreen189();
    private static final int LADS_ID = 0x4C414453 + 1; // the pause menu's Lads button is 0x4C414453; vanilla and Forge use 0-14
    private GuiMainMenu screen;
    private List<GuiButton> buttons;
    private GuiButton ladsButton, moreButton, screenshotsButton, switchVersionButton, friendsButton, replaysButton;
    private CompactButton189 switchButton, fullscreenButton, languageButton;
    private final List<GuiButton> seen = new ArrayList<>(), main = new ArrayList<>(), extras = new ArrayList<>();
    private final List<GuiButton> pending = new ArrayList<>(), row = new ArrayList<>();
    private final List<TitleExtrasScreen189.Action> essential = new ArrayList<>();
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
    public GuiButton moreButton() { return moreButton; }
    public GuiButton switchButton() { return switchButton; }
    public GuiButton fullscreenButton() { return fullscreenButton; }

    private int targetWidth() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.displayWidth > 0) {
            return (int) Math.round(mc.displayWidth / 3.0);
        }
        return screen != null ? screen.width : 320;
    }

    private int targetHeight() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.displayHeight > 0) {
            return (int) Math.round(mc.displayHeight / 3.0);
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
        pending.clear();
        row.clear();
        essential.clear();
        initNanos = System.nanoTime();

        buttons.add(ladsButton = new GuiButton(LADS_ID, 0, 0, 1, 1, "Lads Mods"));
        buttons.add(moreButton = new GuiButton(LADS_ID + 2, 0, 0, 1, 1, "More..."));
        screenshotsButton = Screenshots189.active() ? new GuiButton(LADS_ID + 4, 0, 0, 1, 1, "Screenshots") : null;
        if (screenshotsButton != null) buttons.add(screenshotsButton);

        buttons.add(switchVersionButton = new GuiButton(LADS_ID + 6, 0, 0, 1, 1, "Switch Versions"));
        buttons.add(friendsButton = new GuiButton(LADS_ID + 7, 0, 0, 1, 1, "Friends"));
        replaysButton = Loader.isModLoaded("replaymod") ? new GuiButton(LADS_ID + 8, 0, 0, 1, 1, "Replays") : null;
        if (replaysButton != null) buttons.add(replaysButton);

        // Beside the account name and top right
        GuiMainMenu title = screen;
        buttons.add(switchButton = new CompactButton189(LADS_ID + 1, 0, 0, 1, 1, TitleScreenTheme.SWITCH, () -> "switch",
            () -> Minecraft.getMinecraft().displayGuiScreen(new AccountSwitcherScreen189(title))));
        buttons.add(fullscreenButton = EssentialRow189.fullscreen(LADS_ID + 3, screen.width));
        buttons.add(languageButton = new CompactButton189(LADS_ID + 5, screen.width - 50, 6, 20, 20, "Language", () -> "globe",
            () -> Minecraft.getMinecraft().displayGuiScreen(new GuiLanguage(title, Minecraft.getMinecraft().gameSettings, Minecraft.getMinecraft().getLanguageManager()))));

        lastFrame = 0;
    }

    /** Lays the buttons out on the first frame, when Essential and other mods have added theirs, and again when they change. */
    private void ensureLayout() {
        boolean bound = false;
        if (!pending.isEmpty() && System.nanoTime() - initNanos < 5_000_000_000L)
            for (GuiButton proxy : new ArrayList<>(pending))
                if (EssentialRow189.collect(proxy, essential)) {
                    pending.remove(proxy);
                    bound = true;
                }
        int targetW = targetWidth();
        int targetH = targetHeight();
        float ms = matrixScale();

        if (!bound && layout != null && layoutWidth == targetW && layoutHeight == targetH && seen.equals(buttons)) return;
        buttons.removeAll(row);
        main.clear();
        for (GuiButton button : new ArrayList<>(buttons)) {
            if (button == switchButton || button == fullscreenButton || button == languageButton) continue;
            if (button == ladsButton || button == moreButton || button == screenshotsButton
                || button == switchVersionButton || button == friendsButton || button == replaysButton
                || vanilla(button, 1, 11, 2, 0, 4, 6)) main.add(button);
            else {
                buttons.remove(button);
                button.visible = false;
                if (EssentialActions189.isEssential(button)) {
                    if (!EssentialRow189.collect(button, essential) && !pending.contains(button)) pending.add(button);
                } else if (!extras.contains(button)) extras.add(button);
            }
        }
        main.sort(Comparator.comparingInt(this::order));
        layout = TitleScreenTheme.layout(targetW, targetH, main.size(), !essential.isEmpty());
        layoutWidth = targetW;
        layoutHeight = targetH;

        for (int i = 0; i < main.size(); i++) {
            GuiButton button = main.get(i);
            TitleScreenTheme.Rect rect = layout.buttons().get(i);
            button.xPosition = (int) Math.round(rect.x() * ms);
            button.yPosition = (int) Math.round(rect.y() * ms);
            button.width = (int) Math.round(rect.width() * ms);
            button.height = (int) Math.round(rect.height() * ms);
            button.visible = true;
        }

        fullscreenButton.xPosition = (int) Math.round((targetW - 26) * ms);
        fullscreenButton.yPosition = (int) Math.round(6 * ms);
        fullscreenButton.width = (int) Math.round(20 * ms);
        fullscreenButton.height = (int) Math.round(20 * ms);

        languageButton.xPosition = (int) Math.round((targetW - 50) * ms);
        languageButton.yPosition = (int) Math.round(6 * ms);
        languageButton.width = (int) Math.round(20 * ms);
        languageButton.height = (int) Math.round(20 * ms);

        row.clear();
        row.addAll(EssentialRow189.place(essential, LADS_ID + 10, screen.height, TitleScreenTheme.essentialRowWidth(layout)));
        buttons.addAll(row);
        seen.clear();
        seen.addAll(buttons);
        if (!logged) {
            logged = true;
            LogManager.getLogger("TheLadsCore-1.8.9").info("custom title initialized for Minecraft 1.8.9: {} native widgets, {} more", main.size(), extras.size());
        }
    }

    private int order(GuiButton button) {
        if (button == ladsButton) return 2;
        if (button == replaysButton) return 6;
        if (button == friendsButton) return 7;
        if (button == moreButton) return 8;
        if (button.id == 4) return 9; // Quit Game
        if (button == switchVersionButton) return 10;
        switch (button.id) {
            case 1: case 11: return 0;
            case 2: return 1;
            case 6: return 3; // Forge's Mods
            case 0: return 4;
            default: return button == screenshotsButton ? 5 : 11;
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
        if (button.id == LADS_ID + 7) return "friends";
        if (button.id == LADS_ID + 8) return "replay";
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
        EssentialActions189.suppressOverlay(screen);
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

        if (switchBox != null) {
            switchButton.xPosition = (int) Math.round(switchBox.x() * ms);
            switchButton.yPosition = (int) Math.round(switchBox.y() * ms);
            switchButton.width = (int) Math.round(switchBox.width() * ms);
            switchButton.height = (int) Math.round(switchBox.height() * ms);
        }

        for (GuiButton button : row) button.drawButton(mc, event.mouseX, event.mouseY);
        switchButton.drawButton(mc, event.mouseX, event.mouseY);
        fullscreenButton.drawButton(mc, event.mouseX, event.mouseY);
        if (languageButton != null) languageButton.drawButton(mc, event.mouseX, event.mouseY);
    }

    private static void openFriends(GuiMainMenu parent) {
        try {
            Class<?> util = Class.forName("gg.essential.util.GuiUtil");
            Object inst = util.getField("INSTANCE").get(null);
            Class<?> screenCls = Class.forName("gg.essential.gui.friends.SocialScreen");
            Object scr = screenCls.getConstructor().newInstance();
            util.getMethod("openScreen", net.minecraft.client.gui.GuiScreen.class).invoke(inst, scr);
            return;
        } catch (Throwable ignored) {}
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
        else if (event.button == screenshotsButton) Screenshots189.open(screen);
        else if (event.button == moreButton) mc.displayGuiScreen(new TitleExtrasScreen189(screen, TitleExtrasScreen189.of(screen, extras, LadsTitleScreen189::label)));
        else if (event.button == switchVersionButton) mc.displayGuiScreen(new VersionSwitchScreen189(screen));
        else if (event.button == friendsButton) openFriends(screen);
        else if (event.button == replaysButton) openReplays(screen);
        else if (event.button.id == 4) mc.displayGuiScreen(new QuitConfirmScreen189(screen));
        else if (event.button == switchButton || event.button == fullscreenButton || event.button == languageButton || row.contains(event.button)) ((CompactButton189) event.button).press();
        else return;
        if (clicked) event.button.playPressSound(mc.getSoundHandler());
        event.setCanceled(true);
    }
}
