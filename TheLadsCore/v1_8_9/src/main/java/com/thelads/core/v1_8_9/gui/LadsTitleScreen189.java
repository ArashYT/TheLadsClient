package com.thelads.core.v1_8_9.gui;

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
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.LogManager;

/**
 * The Lads title screen on 1.8.9 (TitleScreenMixin on the other versions), through Forge's screen events so Essential's and
 * OptiFine's GuiMainMenu changes keep working: themed artwork, Singleplayer, Multiplayer, Lads Mods, Forge's Mods, Options,
 * Screenshots, More and Quit as Lads buttons; Essential's actions in a row above the account name, Switch (accounts) beside it
 * and a fullscreen toggle top right; every other button (Language, Realms) behind More. "TitleScreen" off: vanilla.
 */
public final class LadsTitleScreen189 {
    public static final LadsTitleScreen189 INSTANCE = new LadsTitleScreen189();
    private static final int LADS_ID = 0x4C414453 + 1; // the pause menu's Lads button is 0x4C414453; vanilla and Forge use 0-14
    private GuiMainMenu screen;
    private List<GuiButton> buttons;
    private GuiButton ladsButton, moreButton, screenshotsButton;
    private CompactButton189 switchButton, fullscreenButton;
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
        // The gallery, as the other versions' title button for it; only while BetterScreenshots is on.
        screenshotsButton = Screenshots189.active() ? new GuiButton(LADS_ID + 4, 0, 0, 1, 1, "Screenshots") : null;
        if (screenshotsButton != null) buttons.add(screenshotsButton);
        // Beside the account name (placed each frame after it) and top right, as on the pause menu; not in the menu grid.
        GuiMainMenu title = screen;
        buttons.add(switchButton = new CompactButton189(LADS_ID + 1, 0, 0, 1, 1, TitleScreenTheme.SWITCH, () -> "switch",
            () -> Minecraft.getMinecraft().displayGuiScreen(new AccountSwitcherScreen189(title))));
        buttons.add(fullscreenButton = EssentialRow189.fullscreen(LADS_ID + 3, screen.width));
        lastFrame = 0;
    }

    /** Lays the buttons out on the first frame, when Essential and other mods have added theirs, and again when they change. */
    private void ensureLayout() {
        // Essential binds its buttons a moment after the screen opens; the row is rebuilt once they are ready.
        boolean bound = false;
        if (!pending.isEmpty() && System.nanoTime() - initNanos < 5_000_000_000L)
            for (GuiButton proxy : new ArrayList<>(pending))
                if (EssentialRow189.collect(proxy, essential)) {
                    pending.remove(proxy);
                    bound = true;
                }
        if (!bound && layout != null && layoutWidth == screen.width && layoutHeight == screen.height && seen.equals(buttons)) return;
        buttons.removeAll(row);
        main.clear();
        for (GuiButton button : new ArrayList<>(buttons)) {
            if (button == switchButton || button == fullscreenButton) continue;
            if (button == ladsButton || button == moreButton || button == screenshotsButton || vanilla(button, 1, 11, 2, 0, 4, 6)) main.add(button);
            else {
                // Off the screen, as the other versions remove them (Essential's proxies override mousePressed).
                buttons.remove(button);
                button.visible = false;
                if (EssentialActions189.isEssential(button)) {
                    // Essential's actions get their own row above the account name.
                    if (!EssentialRow189.collect(button, essential) && !pending.contains(button)) pending.add(button);
                } else if (!extras.contains(button)) extras.add(button);
            }
        }
        main.sort(Comparator.comparingInt(this::order));
        layout = TitleScreenTheme.layout(screen.width, screen.height, main.size(), !essential.isEmpty());
        layoutWidth = screen.width;
        layoutHeight = screen.height;
        for (int i = 0; i < main.size(); i++) {
            GuiButton button = main.get(i);
            TitleScreenTheme.Rect rect = layout.buttons().get(i);
            button.xPosition = rect.x();
            button.yPosition = rect.y();
            button.width = rect.width();
            button.height = rect.height();
            button.visible = true;
        }
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

    /** In pairs below Singleplayer and Multiplayer: Lads Mods | Mods, Options | Screenshots, More... | Quit Game. */
    private int order(GuiButton button) {
        if (button == ladsButton) return 3;
        if (button == screenshotsButton) return 6;
        if (button == moreButton) return 7;
        switch (button.id) {
            case 1: case 11: return 0;
            case 2: return 1;
            case 6: return 4; // Forge's Mods
            case 0: return 5;
            default: return 8; // Quit
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
        switch (button.id) {
            case 1: case 11: return "play";
            case 2: return "server";
            case 6: return "mods";
            case 0: return "settings";
            case 4: return "quit";
            default: return "more";
        }
    }

    /** As drawn (the native button keeps its text): the other versions' short English labels. */
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

    /**
     * Over GuiMainMenu's own frame rather than instead of it: its GuiScreen.drawScreen is where Essential builds its menu, whose
     * actions More offers. The opaque Lads artwork covers the vanilla panorama, logo, splash and branding.
     */
    @SubscribeEvent
    public void draw(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (screen == null || event.gui != screen) return;
        EssentialActions189.suppressOverlay(screen);
        long now = System.nanoTime();
        float elapsed = lastFrame == 0 ? 0 : (float) Math.min(0.1, (now - lastFrame) / 1e9);
        lastFrame = now;
        seconds += elapsed;
        Minecraft mc = Minecraft.getMinecraft();
        GuiLadsAdapter g = new GuiLadsAdapter(mc.fontRendererObj, screen.width, screen.height);
        TitleScreenTheme.Rect switchBox = TitleScreenTheme.renderBackground(g, layout, mc.getSession().getUsername(),
            "1.8.9" + (mc.isDemo() ? " Demo" : ""), false, seconds, true);
        switchButton.xPosition = switchBox.x();
        switchButton.yPosition = switchBox.y();
        switchButton.width = switchBox.width();
        switchButton.height = switchBox.height();
        for (GuiButton button : main)
            TitleExtrasScreen189.drawButton(g, button, label(button), icon(button), vanilla(button, 1), event.mouseX, event.mouseY, elapsed);
        for (GuiButton button : row) button.drawButton(mc, event.mouseX, event.mouseY);
        switchButton.drawButton(mc, event.mouseX, event.mouseY);
        fullscreenButton.drawButton(mc, event.mouseX, event.mouseY);
    }

    @SubscribeEvent
    public void action(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (screen == null || event.gui != screen) return;
        Minecraft mc = Minecraft.getMinecraft();
        boolean clicked = mc.currentScreen == screen; // not pressed from the More screen, which played the click sound
        if (event.button == ladsButton) mc.displayGuiScreen(new LadsSettingsScreen189(screen));
        else if (event.button == screenshotsButton) Screenshots189.open(screen);
        else if (event.button == moreButton) mc.displayGuiScreen(new TitleExtrasScreen189(screen, TitleExtrasScreen189.of(screen, extras, LadsTitleScreen189::label)));
        else if (event.button == switchButton || event.button == fullscreenButton || row.contains(event.button)) ((CompactButton189) event.button).press();
        else return;
        if (clicked) event.button.playPressSound(mc.getSoundHandler()); // a cancelled press skips GuiScreen's
        event.setCanceled(true);
    }
}
