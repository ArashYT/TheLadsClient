package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.gui.LadsPauseButton;
import com.thelads.core.v1_8_9.gui.LadsSettingsScreen189;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiScreen;
import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A full-width "Lads Client" row below the pause menu's last row (Save and Quit), as on the other versions. */
@Mixin(GuiIngameMenu.class)
public abstract class GuiIngameMenuMixin extends GuiScreen implements LadsPauseButton {
    @Unique private static final int LADS_BUTTON_ID = 0x4C414453; // "LADS"; vanilla and Forge use 0-12
    @Unique private static boolean ladsLogged;
    @Unique private GuiButton ladsButton;

    @Inject(method = "initGui", at = @At("TAIL"), require = 1)
    private void ladsAddPauseButton(CallbackInfo ci) {
        ladsButton = new GuiButton(LADS_BUTTON_ID, width / 2 - 100, height / 4 + 144 - 16, "Lads Client");
        buttonList.add(ladsButton);
        if (!ladsLogged) {
            ladsLogged = true;
            LogManager.getLogger("TheLadsCore").info("Lads Client pause-menu button initialized");
        }
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsOpenMenu(GuiButton button, CallbackInfo ci) {
        if (button != ladsButton) return;
        mc.displayGuiScreen(new LadsSettingsScreen189(this));
        ci.cancel();
    }

    @Override
    public GuiButton ladsButton() {
        return ladsButton;
    }
}
