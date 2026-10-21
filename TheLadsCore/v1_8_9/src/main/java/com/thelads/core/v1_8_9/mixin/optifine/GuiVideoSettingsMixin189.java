package com.thelads.core.v1_8_9.mixin.optifine;

import com.thelads.core.v1_8_9.gui.GuiOptiFineSettings189;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiVideoSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects OptiFine settings navigation buttons into vanilla Video Settings screen:
 * - Direct row of OptiFine sub-page buttons (Details, Animations, Quality, Performance, Other)
 * - Master OptiFine Settings entry next to Done
 */
@Mixin(GuiVideoSettings.class)
public abstract class GuiVideoSettingsMixin189 extends GuiScreen {
    private static final int BTN_OF_MASTER = 9140;
    private static final int BTN_OF_DETAILS = 9141;
    private static final int BTN_OF_ANIM = 9142;
    private static final int BTN_OF_QUALITY = 9143;
    private static final int BTN_OF_PERF = 9144;
    private static final int BTN_OF_OTHER = 9145;

    @Inject(method = "initGui", at = @At("TAIL"))
    private void lads$addOfButtons(CallbackInfo ci) {
        // Adjust Done button to fit beside OptiFine Settings button
        for (GuiButton btn : this.buttonList) {
            if (btn.id == 200) {
                btn.width = 150;
                btn.xPosition = this.width / 2 - 155;
                break;
            }
        }

        // Master entry button
        this.buttonList.add(new GuiButton(BTN_OF_MASTER, this.width / 2 + 5, this.height - 27, 150, 20, "OptiFine Settings..."));

        // Sub-page quick buttons
        int x0 = this.width / 2 - 155;
        int y0 = this.height - 50;
        this.buttonList.add(new GuiButton(BTN_OF_DETAILS, x0, y0, 60, 20, "Details..."));
        this.buttonList.add(new GuiButton(BTN_OF_ANIM, x0 + 62, y0, 60, 20, "Anim..."));
        this.buttonList.add(new GuiButton(BTN_OF_QUALITY, x0 + 124, y0, 60, 20, "Quality..."));
        this.buttonList.add(new GuiButton(BTN_OF_PERF, x0 + 186, y0, 60, 20, "Perf..."));
        this.buttonList.add(new GuiButton(BTN_OF_OTHER, x0 + 248, y0, 62, 20, "Other..."));
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true)
    private void lads$ofActionPerformed(GuiButton button, CallbackInfo ci) {
        if (!button.enabled) return;

        if (button.id == BTN_OF_MASTER || button.id == BTN_OF_DETAILS) {
            this.mc.displayGuiScreen(new GuiOptiFineSettings189(this, GuiOptiFineSettings189.SubPage.DETAILS));
            ci.cancel();
        } else if (button.id == BTN_OF_ANIM) {
            this.mc.displayGuiScreen(new GuiOptiFineSettings189(this, GuiOptiFineSettings189.SubPage.ANIMATIONS));
            ci.cancel();
        } else if (button.id == BTN_OF_QUALITY) {
            this.mc.displayGuiScreen(new GuiOptiFineSettings189(this, GuiOptiFineSettings189.SubPage.QUALITY));
            ci.cancel();
        } else if (button.id == BTN_OF_PERF) {
            this.mc.displayGuiScreen(new GuiOptiFineSettings189(this, GuiOptiFineSettings189.SubPage.PERFORMANCE));
            ci.cancel();
        } else if (button.id == BTN_OF_OTHER) {
            this.mc.displayGuiScreen(new GuiOptiFineSettings189(this, GuiOptiFineSettings189.SubPage.OTHER));
            ci.cancel();
        }
    }
}
