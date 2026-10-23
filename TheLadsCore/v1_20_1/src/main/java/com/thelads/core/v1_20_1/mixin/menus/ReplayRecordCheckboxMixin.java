package com.thelads.core.v1_20_1.mixin.menus;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * ReplayMod's "Record Server" checkbox sits top right on the server list (width - w - 5, 5), under ViaFabricPlus' button. There it
 * moves to the free strip between the list (ends at height - 64) and the buttons (start at height - 52). Without ReplayMod this does
 * nothing; a ReplayMod whose layout changed is left alone (require 0).
 */
@Pseudo
@Mixin(targets = "com.replaymod.recording.handler.GuiHandler$1", remap = false)
public abstract class ReplayRecordCheckboxMixin {
    @ModifyArg(method = "layout(Lcom/replaymod/lib/de/johni0702/minecraft/gui/container/GuiScreen;II)V",
        at = @At(value = "INVOKE", target = "Lcom/replaymod/recording/handler/GuiHandler$1;pos(Lcom/replaymod/lib/de/johni0702/minecraft/gui/element/GuiElement;II)V"),
        index = 2, require = 0)
    private int ladsBelowServerList(int y) {
        Screen screen = Minecraft.getInstance().screen;
        return screen instanceof JoinMultiplayerScreen ? screen.height - 63 : y;
    }
}
