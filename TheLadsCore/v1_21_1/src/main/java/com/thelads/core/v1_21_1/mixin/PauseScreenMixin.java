package com.thelads.core.v1_21_1.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.thelads.core.v1_21_1.gui.LadsSettingsScreen121;
import com.thelads.core.v1_21_1.gui.PauseMultiplayer;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
    protected PauseScreenMixin() {
        super(Component.empty());
    }

    @Inject(method = "createPauseMenu()V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/layouts/GridLayout;arrangeElements()V"),
        require = 1)
    private void ladsAddPauseButtons(CallbackInfo ci, @Local GridLayout.RowHelper rows) {
        // Let vanilla lay out and register the extra rows with all original buttons (Flashback's rows follow them).
        // This method is not called by PauseScreen(false), which intentionally has no menu.
        rows.addChild(Button.builder(Component.literal("Lads Client"),
            button -> minecraft.setScreen(new LadsSettingsScreen121(this))).width(204).build(), 2);
        rows.addChild(Button.builder(Component.translatable("menu.multiplayer"),
            button -> PauseMultiplayer.open(this)).width(204).build(), 2);
        LoggerFactory.getLogger("TheLadsCore").info("Lads Client pause-menu button initialized");
    }
}
