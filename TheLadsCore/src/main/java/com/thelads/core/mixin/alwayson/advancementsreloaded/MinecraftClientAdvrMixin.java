package com.thelads.core.mixin.alwayson.advancementsreloaded;

import com.thelads.core.features.alwayson.advancementsreloaded.screens.AdvancementReloadedScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.multiplayer.ClientAdvancements;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Minecraft.class)
public class MinecraftClientAdvrMixin {
    @Redirect(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V"))
    private void replaceAdvancementsScreen(Minecraft client, Screen screen) {
        if (screen instanceof AdvancementsScreen && client.player != null && client.player.connection != null &&
            com.thelads.core.config.ModuleManager.getInstance().getModule("AdvancementsReloaded").isEnabled()) {
            ClientAdvancements advancementManager = client.player.connection.getAdvancements();
            client.setScreenAndShow(new AdvancementReloadedScreen(advancementManager));
        } else {
            client.setScreenAndShow(screen);
        }
    }
}
