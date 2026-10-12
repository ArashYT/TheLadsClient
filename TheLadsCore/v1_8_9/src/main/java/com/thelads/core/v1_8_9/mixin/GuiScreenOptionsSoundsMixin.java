package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.gui.ModuleVolumeSlider189;
import net.minecraft.client.audio.SoundCategory;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenOptionsSounds;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Music &amp; Sounds: Kill Banners and Combo Sounds sliders on the row under the game's own volumes (ModuleVolumeSlider189). */
@Mixin(GuiScreenOptionsSounds.class)
public abstract class GuiScreenOptionsSoundsMixin extends GuiScreen {
    @Inject(method = "initGui", at = @At("TAIL"))
    private void ladsModuleVolumes(CallbackInfo ci) {
        // As initGui places them: Master fills the first row, then two a row; ours start the next free row.
        int slot = 2 + SoundCategory.values().length - 1;
        slot += slot % 2;
        for (int slider = 0; slider < 2; slider++, slot++)
            buttonList.add(new ModuleVolumeSlider189(1900 + slider, width / 2 - 155 + slot % 2 * 160, height / 6 - 12 + 24 * (slot >> 1), slider));
    }
}
