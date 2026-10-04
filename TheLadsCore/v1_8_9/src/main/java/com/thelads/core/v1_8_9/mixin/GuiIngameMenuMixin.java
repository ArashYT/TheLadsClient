package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.client.title.ButtonLift;
import com.thelads.core.client.title.PauseMenuLayout;
import com.thelads.core.client.title.TitleScreenTheme;
import com.thelads.core.v1_8_9.adapter.GuiLadsAdapter;
import com.thelads.core.v1_8_9.gui.CompactButton189;
import com.thelads.core.v1_8_9.gui.EssentialActions189;
import com.thelads.core.v1_8_9.gui.EssentialRow189;
import com.thelads.core.v1_8_9.gui.LadsPauseButton;
import com.thelads.core.v1_8_9.gui.LadsSettingsScreen189;
import com.thelads.core.v1_8_9.gui.PauseMultiplayer189;
import com.thelads.core.v1_8_9.gui.TitleExtrasScreen189;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Lads pause menu, as on the other versions: Lads theme and logo, the buttons in groups (PauseMenuLayout), "Lads Client" and
 * Multiplayer rows, Essential's actions in a row above the account name, other small buttons behind "Extras...", and a
 * top-right fullscreen toggle.
 */
@Mixin(GuiIngameMenu.class)
public abstract class GuiIngameMenuMixin extends GuiScreen implements LadsPauseButton {
    @Unique private static final int LADS_BUTTON_ID = 0x4C414453; // "LADS"; vanilla and Forge use 0-12
    @Unique private static boolean ladsLogged;
    @Unique private GuiButton ladsButton, ladsMultiplayerButton, ladsExtrasButton;
    @Unique private List<GuiButton> ladsExtras;
    @Unique private int ladsLaidOut;
    @Unique private List<TitleExtrasScreen189.Action> ladsEssential;
    @Unique private List<GuiButton> ladsEssentialPending, ladsRow;
    @Unique private GuiButton ladsFullscreenButton;
    @Unique private long ladsInitNanos;

    @Inject(method = "initGui", at = @At("TAIL"), require = 1)
    private void ladsAddPauseButtons(CallbackInfo ci) {
        ladsButton = new GuiButton(LADS_BUTTON_ID, width / 2 - 100, height / 4 + 144 - 16, "Lads Client");
        buttonList.add(ladsButton);
        ladsMultiplayerButton = new GuiButton(LADS_BUTTON_ID + 4, width / 2 - 100, height / 4 + 168 - 16, I18n.format("menu.multiplayer"));
        buttonList.add(ladsMultiplayerButton);
        ladsExtras = new ArrayList<>();
        ladsExtrasButton = null;
        ladsLaidOut = -1;
        ladsEssential = new ArrayList<>();
        ladsEssentialPending = new ArrayList<>();
        ladsRow = new ArrayList<>();
        ladsInitNanos = System.nanoTime();
        ladsFullscreenButton = EssentialRow189.fullscreen(LADS_BUTTON_ID + 6, width);
        buttonList.add(ladsFullscreenButton);
        if (!ladsLogged) {
            ladsLogged = true;
            LogManager.getLogger("TheLadsCore").info("Lads Client pause-menu button initialized");
        }
    }

    /** On the first frame, when Forge mods and Essential have added their buttons, and again when the button list changes. */
    @Unique
    private void ladsLayout() {
        boolean changed = ladsLaidOut != buttonList.size();
        // Essential binds its buttons a moment after the screen opens; the row is rebuilt once they are ready.
        if (!ladsEssentialPending.isEmpty() && System.nanoTime() - ladsInitNanos < 5_000_000_000L)
            for (GuiButton proxy : new ArrayList<>(ladsEssentialPending))
                if (EssentialRow189.collect(proxy, ladsEssential)) {
                    ladsEssentialPending.remove(proxy);
                    changed = true;
                }
        for (GuiButton button : new ArrayList<>(buttonList)) {
            if (button == ladsExtrasButton || button == ladsFullscreenButton || ladsRow.contains(button)) continue;
            // Off the screen, as the other versions remove them (Essential's proxies override mousePressed).
            if (EssentialActions189.isEssential(button)) {
                buttonList.remove(button);
                button.visible = false;
                changed = true;
                // Essential's actions get their own row above the account name.
                if (!EssentialRow189.collect(button, ladsEssential) && !ladsEssentialPending.contains(button)) ladsEssentialPending.add(button);
            } else if (button.width <= 30) {
                buttonList.remove(button);
                button.visible = false;
                if (!ladsExtras.contains(button)) ladsExtras.add(button);
                changed = true;
            }
        }
        if (!changed) return;
        if (!ladsExtras.isEmpty() && ladsExtrasButton == null)
            buttonList.add(ladsExtrasButton = new GuiButton(LADS_BUTTON_ID + 5, 0, 0, 204, 20, "Extras..."));
        buttonList.removeAll(ladsRow);
        ladsRow = EssentialRow189.place(ladsEssential, LADS_BUTTON_ID + 10, height, width - 32);
        buttonList.addAll(ladsRow);
        ladsLaidOut = buttonList.size();
        List<GuiButton> grid = new ArrayList<>();
        List<PauseMenuLayout.Slot> slots = new ArrayList<>();
        for (GuiButton button : buttonList)
            if (button != ladsFullscreenButton && !ladsRow.contains(button)) { grid.add(button); slots.add(ladsSlot(button)); }
        // In groups: Back to Game, then Achievements/Statistics, Options/Lads Client, Multiplayer/LAN, Extras, Save and Quit apart.
        int bottom = height - 32 - (ladsEssential.isEmpty() ? 0 : TitleScreenTheme.ROW_SPACE);
        int top = Math.max(62, 12 + Math.min(64, height / 6) + 18);
        List<PauseMenuLayout.Box> boxes = PauseMenuLayout.arrange(slots, width, top, bottom);
        for (int i = 0; i < grid.size(); i++) {
            GuiButton button = grid.get(i);
            PauseMenuLayout.Box box = boxes.get(i);
            button.xPosition = box.x();
            button.yPosition = box.y();
            button.width = box.width();
            button.height = box.height();
            ButtonLift.enable(button, PauseMenuLayout.icon(slots.get(i)));
        }
    }

    @Unique
    private PauseMenuLayout.Slot ladsSlot(GuiButton button) {
        if (button == ladsButton) return PauseMenuLayout.Slot.LADS;
        if (button == ladsMultiplayerButton) return PauseMenuLayout.Slot.MULTIPLAYER;
        if (button == ladsExtrasButton) return PauseMenuLayout.Slot.EXTRAS;
        if (button.getClass() == GuiButton.class)
            switch (button.id) {
                case 4: return PauseMenuLayout.Slot.BACK;
                case 5: return PauseMenuLayout.Slot.ADVANCEMENTS; // Achievements
                case 6: return PauseMenuLayout.Slot.STATS;
                case 0: return PauseMenuLayout.Slot.OPTIONS;
                case 7: return PauseMenuLayout.Slot.WORLD; // Open to LAN
                case 1: return PauseMenuLayout.Slot.QUIT; // Save and Quit / Disconnect
            }
        return PauseMenuLayout.Slot.OTHER; // Forge's Mod Options and other mods' buttons
    }

    @Inject(method = "drawScreen", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsDrawPause(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        EssentialActions189.suppressOverlay(this);
        ladsLayout();
        // The "Game menu" caption would sit under the grid: the Lads logo and theme replace it, then the buttons draw.
        drawDefaultBackground();
        drawRect(0, 0, width, height, 0xB8100B10);
        drawRect(0, 0, width, 2, 0xFFCF1535);
        GuiLadsAdapter g = new GuiLadsAdapter(fontRendererObj, width, height);
        TitleScreenTheme.renderLogo(g, width / 2, 12, Math.min(64, height / 6));
        drawRect(16, height - 29, width - 16, height - 28, 0x2944202A);
        TitleScreenTheme.renderAccount(g, height, mc.getSession().getUsername(), Math.min(170, width - 32));
        super.drawScreen(mouseX, mouseY, partialTicks);
        ci.cancel();
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsPauseAction(GuiButton button, CallbackInfo ci) {
        if (button == ladsButton) mc.displayGuiScreen(new LadsSettingsScreen189(this));
        else if (button == ladsMultiplayerButton) PauseMultiplayer189.open(this);
        else if (button == ladsExtrasButton) mc.displayGuiScreen(new TitleExtrasScreen189(this, TitleExtrasScreen189.of(this, ladsExtras)));
        else if (button instanceof CompactButton189) ((CompactButton189) button).press();
        else return;
        ci.cancel();
    }

    @Override
    public GuiButton ladsButton() {
        return ladsButton;
    }

    @Override
    public GuiButton ladsMultiplayerButton() {
        return ladsMultiplayerButton;
    }
}
