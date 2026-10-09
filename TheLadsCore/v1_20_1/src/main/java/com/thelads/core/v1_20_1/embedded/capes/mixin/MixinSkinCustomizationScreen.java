// Adapted from Capes 1.5.10+26.3 by Cael (LGPL-2.1-only); modified by The Lads: ported to Minecraft 1.20.1 from the Lads 26.3 embedded copy.
package com.thelads.core.v1_20_1.embedded.capes.mixin;

import com.thelads.core.v1_20_1.embedded.capes.menu.CapeButton;
import com.thelads.core.v1_20_1.embedded.capes.menu.SelectorMenu;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.OptionsSubScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.SkinCustomizationScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkinCustomizationScreen.class)
public abstract class MixinSkinCustomizationScreen extends OptionsSubScreen {

    public MixinSkinCustomizationScreen(Screen parent, Options gameOptions, Component title) {
        super(parent, gameOptions, title);
    }

    /** Next to the first column of toggles, where 26.3 puts it next to the options list; in the corner when the window is too narrow for that. */
    @Inject(method = "init", at = @At("RETURN"))
    public void addCapeButton(CallbackInfo info) {
        int x = this.width / 2 - 155 - 25;
        int y = this.height / 6;
        if (x < 2) {
            x = 2;
            y = 2;
        }
        this.addRenderableWidget(new CapeButton(x, y, button -> this.minecraft.setScreen(new SelectorMenu(this, this.options))));
    }
}
