package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.client.title.TitleScreenTheme;
import com.thelads.core.v1_8_9.adapter.GuiLadsAdapter;
import com.thelads.core.v1_8_9.gui.EssentialActions189;
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
 * The Lads pause menu, as on the other versions: Lads theme and logo, every button in a 1-2 column grid, "Lads Client" and
 * Multiplayer rows, Essential's and other small buttons behind "Essential & extras...".
 */
@Mixin(GuiIngameMenu.class)
public abstract class GuiIngameMenuMixin extends GuiScreen implements LadsPauseButton {
    @Unique private static final int LADS_BUTTON_ID = 0x4C414453; // "LADS"; vanilla and Forge use 0-12
    @Unique private static boolean ladsLogged;
    @Unique private GuiButton ladsButton, ladsMultiplayerButton, ladsExtrasButton;
    @Unique private List<GuiButton> ladsExtras;
    @Unique private int ladsLaidOut;

    @Inject(method = "initGui", at = @At("TAIL"), require = 1)
    private void ladsAddPauseButtons(CallbackInfo ci) {
        ladsButton = new GuiButton(LADS_BUTTON_ID, width / 2 - 100, height / 4 + 144 - 16, "Lads Client");
        buttonList.add(ladsButton);
        ladsMultiplayerButton = new GuiButton(LADS_BUTTON_ID + 4, width / 2 - 100, height / 4 + 168 - 16, I18n.format("menu.multiplayer"));
        buttonList.add(ladsMultiplayerButton);
        ladsExtras = new ArrayList<>();
        ladsExtrasButton = null;
        ladsLaidOut = -1;
        if (!ladsLogged) {
            ladsLogged = true;
            LogManager.getLogger("TheLadsCore").info("Lads Client pause-menu button initialized");
        }
    }

    /** On the first frame, when Forge mods and Essential have added their buttons, and again when the button list changes. */
    @Unique
    private void ladsLayout() {
        boolean changed = ladsLaidOut != buttonList.size();
        for (GuiButton button : new ArrayList<>(buttonList))
            if (button != ladsExtrasButton && (EssentialActions189.isEssential(button) || button.width <= 30)) {
                // Off the screen, as the other versions remove them (Essential's proxies override mousePressed).
                buttonList.remove(button);
                button.visible = false;
                if (!ladsExtras.contains(button)) ladsExtras.add(button);
                changed = true;
            }
        if (!changed) return;
        if (!ladsExtras.isEmpty() && ladsExtrasButton == null)
            buttonList.add(ladsExtrasButton = new GuiButton(LADS_BUTTON_ID + 5, 0, 0, 204, 20, "Essential & extras..."));
        ladsLaidOut = buttonList.size();
        List<GuiButton> grid = new ArrayList<>(); // by ladsOrder, stable (no lambdas in this mixin for Mixin 0.7)
        for (int order = 0; order <= 8; order++)
            for (GuiButton button : buttonList) if (ladsOrder(button) == order) grid.add(button);
        int columns = width < 380 ? 1 : 2;
        int total = Math.min(width - 32, 360), gap = 6, cellWidth = (total - gap * (columns - 1)) / columns;
        int rows = (grid.size() + columns - 1) / columns;
        int top = Math.max(62, Math.min(height / 3, height - rows * 29 - 12));
        int rowHeight = Math.max(17, Math.min(27, (height - top - 10) / Math.max(1, rows) - 3));
        for (int i = 0; i < grid.size(); i++) {
            GuiButton button = grid.get(i);
            button.xPosition = (width - total) / 2 + i % columns * (cellWidth + gap);
            button.yPosition = top + i / columns * (rowHeight + 3);
            button.width = cellWidth;
            button.height = rowHeight;
        }
    }

    @Unique
    private int ladsOrder(GuiButton button) {
        if (button == ladsButton) return 7;
        if (button == ladsMultiplayerButton) return 3;
        if (button.getClass() == GuiButton.class)
            switch (button.id) {
                case 4: return 0;  // Back to Game
                case 5: return 1;  // Achievements
                case 6: return 2;  // Statistics
                case 0: return 3;  // Options
                case 7: case 12: return 4; // Open to LAN, Mod Options
                case 1: return 6;  // Save and Quit / Disconnect
            }
        return 8;
    }

    @Inject(method = "drawScreen", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsDrawPause(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        EssentialActions189.suppressOverlay(this);
        ladsLayout();
        // The "Game menu" caption would sit under the grid: the Lads logo and theme replace it, then the buttons draw.
        drawDefaultBackground();
        drawRect(0, 0, width, height, 0xB8100B10);
        drawRect(0, 0, width, 2, 0xFFCF1535);
        TitleScreenTheme.renderLogo(new GuiLadsAdapter(fontRendererObj, width, height), width / 2, 12, Math.min(64, height / 6));
        super.drawScreen(mouseX, mouseY, partialTicks);
        ci.cancel();
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsPauseAction(GuiButton button, CallbackInfo ci) {
        if (button == ladsButton) mc.displayGuiScreen(new LadsSettingsScreen189(this));
        else if (button == ladsMultiplayerButton) PauseMultiplayer189.open(this);
        else if (button == ladsExtrasButton) mc.displayGuiScreen(new TitleExtrasScreen189(this, TitleExtrasScreen189.of(this, ladsExtras)));
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
