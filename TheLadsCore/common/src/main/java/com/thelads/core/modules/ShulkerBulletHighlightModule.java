package com.thelads.core.modules;

import com.thelads.core.client.ShulkerBulletHighlight;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.Module;

public class ShulkerBulletHighlightModule extends Module {
    public final ColorOption bulletColor;
    public final BoolOption glowEffect;

    public ShulkerBulletHighlightModule() {
        super(ShulkerBulletHighlight.MODULE_NAME, "Highlight approaching shulker bullets in bright outline and glow colors.");
        bulletColor = addOption(new ColorOption("Bullet Color", false, 0xFFFF0055));
        glowEffect = addOption(new BoolOption("Glow Effect", true));
        ShulkerBulletHighlight.setModule(this);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        ShulkerBulletHighlight.refresh();
    }
}
