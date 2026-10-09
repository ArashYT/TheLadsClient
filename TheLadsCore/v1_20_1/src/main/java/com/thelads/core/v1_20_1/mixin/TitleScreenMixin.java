package com.thelads.core.v1_20_1.mixin;

import com.thelads.core.v1_20_1.gui.LadsSettingsScreen1201;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    protected TitleScreenMixin() {
        super(Component.empty());
    }

    @Inject(method = "init()V", at = @At("TAIL"), require = 1)
    private void ladsAddTitleButton(CallbackInfo ci) {
        this.addRenderableWidget(Button.builder(Component.literal("§cLads §fClient"),
            button -> this.minecraft.setScreen(new LadsSettingsScreen1201(this)))
            .bounds(10, 10, 85, 20)
            .build());
    }
}
