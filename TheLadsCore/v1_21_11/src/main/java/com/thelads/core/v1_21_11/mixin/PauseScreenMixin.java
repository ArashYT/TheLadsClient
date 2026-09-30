package com.thelads.core.v1_21_11.mixin;

import com.thelads.core.v1_21_11.gui.LadsSettingsScreen12111;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
    @Unique private GridLayout.RowHelper ladsPauseRows;

    protected PauseScreenMixin() {
        super(Component.empty());
    }

    @Redirect(method = "createPauseMenu()V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/layouts/GridLayout;createRowHelper(I)Lnet/minecraft/client/gui/layouts/GridLayout$RowHelper;"),
        require = 1)
    private GridLayout.RowHelper ladsCapturePauseRows(GridLayout grid, int columns) {
        ladsPauseRows = grid.createRowHelper(columns);
        return ladsPauseRows;
    }

    @Inject(method = "createPauseMenu()V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/layouts/GridLayout;arrangeElements()V"),
        require = 1)
    private void ladsAddPauseButton(CallbackInfo ci) {
        // Let vanilla lay out and register the extra row with all original buttons.
        // This method is not called by PauseScreen(false), which intentionally has no menu.
        ladsPauseRows.addChild(Button.builder(Component.literal("Lads Client"),
            button -> minecraft.setScreen(new LadsSettingsScreen12111(this))).width(204).build(), 2);
        ladsPauseRows.addChild(Button.builder(Component.translatable("menu.multiplayer"),
            button -> com.thelads.core.v1_21_11.gui.PauseMultiplayer.open(this)).width(204).build(), 2);
        ladsPauseRows = null;
        LoggerFactory.getLogger("TheLadsCore").info("Lads Client pause-menu button initialized");
    }
}
