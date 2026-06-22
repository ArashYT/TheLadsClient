package com.thelads.core.mixin.alwayson.betterstatisticscreen;

import com.thelads.core.features.alwayson.betterstatisticscreen.client.gui.screen.BetterStatsScreen;
import java.util.function.Supplier;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = PauseScreen.class, priority = 1001)
public abstract class PauseScreenBetterStatsMixin extends Screen {
    protected PauseScreenBetterStatsMixin(Component title) {
        super(title);
    }

    @ModifyArg(method = "createPauseMenu", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/PauseScreen;openScreenButton(Lnet/minecraft/network/chat/Component;Ljava/util/function/Supplier;)Lnet/minecraft/client/gui/components/Button;"), index = 1)
    private Supplier<Screen> modifyStatsButton(Supplier<Screen> original) {
        if (original.get() instanceof StatsScreen && this.minecraft.player != null) {
            return () -> {
                if (!com.thelads.core.config.ModuleManager.getInstance().getModule("BetterStats").isEnabled()) {
                    return original.get();
                }
                if (com.mojang.blaze3d.platform.InputConstants.isKeyDown(this.minecraft.getWindow(), 340) ||
                    com.mojang.blaze3d.platform.InputConstants.isKeyDown(this.minecraft.getWindow(), 344)) {
                    return original.get();
                }
                if (this.minecraft.player != null) {
                    return new BetterStatsScreen(this);
                }
                return original.get();
            };
        }
        return original;
    }
}
