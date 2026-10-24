package com.thelads.core.modules;

import com.thelads.core.client.ItemDespawnFlash;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;

public class ItemDespawnFlashModule extends Module {
    public final SliderOption thresholdSeconds;
    public final SliderOption flashSpeed;
    public final ColorOption blinkColor;

    public ItemDespawnFlashModule() {
        super(ItemDespawnFlash.MODULE_NAME, "Flash dropped items approaching the 5-minute despawn threshold.");
        thresholdSeconds = addOption(new SliderOption("Flash Below Seconds", 30, 5, 60, 5));
        flashSpeed = addOption(new SliderOption("Flash Speed", 2, 1, 5, 1));
        blinkColor = addOption(new ColorOption("Blink Color", false, 0xFFFF5555));
        ItemDespawnFlash.setModule(this);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        ItemDespawnFlash.refresh();
    }
}
