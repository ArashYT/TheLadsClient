package com.thelads.core.v1_21_11.mixin;

import com.thelads.core.client.WindowTitle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class WindowTitleMixin {
    @Unique
    private static final WindowTitle.Updater LADS_WINDOW_TITLE = new WindowTitle.Updater("1.21.11", title -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getWindow() != null) {
            mc.getWindow().setTitle(title);
        }
    });

    @Inject(method = "updateTitle()V", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$updateTitle(CallbackInfo ci) {
        lads$refreshTitle();
        ci.cancel();
    }

    @Inject(method = "setScreen", at = @At("TAIL"), require = 1)
    private void lads$afterSetScreen(Screen screen, CallbackInfo ci) {
        lads$refreshTitle();
    }

    @Inject(method = "tick()V", at = @At("TAIL"), require = 1)
    private void lads$afterTick(CallbackInfo ci) {
        lads$refreshTitle();
    }

    @Unique
    private void lads$refreshTitle() {
        Minecraft mc = (Minecraft) (Object) this;
        if (mc.getWindow() == null) return;
        boolean inWorld = mc.level != null;
        boolean localWorld = inWorld && (mc.isLocalServer() || mc.getSingleplayerServer() != null);
        var remote = mc.getCurrentServer();
        Screen screen = mc.screen;
        WindowTitle.Menu menu = screen == null ? WindowTitle.Menu.NONE
            : screen instanceof TitleScreen ? WindowTitle.Menu.TITLE
            : screen instanceof SelectWorldScreen ? WindowTitle.Menu.WORLD_SELECT
            : screen instanceof JoinMultiplayerScreen ? WindowTitle.Menu.SERVER_LIST
            : WindowTitle.Menu.OTHER;
        LADS_WINDOW_TITLE.update(inWorld, localWorld, remote == null ? null : remote.ip, menu);
    }
}
