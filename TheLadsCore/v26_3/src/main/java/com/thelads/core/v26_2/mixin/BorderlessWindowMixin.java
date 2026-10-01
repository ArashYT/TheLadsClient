package com.thelads.core.v26_2.mixin;
import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
/**
 * BorderlessFullscreen on 26.3 (SDL3, no GLFW): vanilla already has the right window on Windows: restored, undecorated,
 * not SDL-fullscreen, one column wider than the display so it is never promoted to exclusive/independent flip, with the
 * framebuffer trimmed to the display. The module selects that path even when Exclusive Fullscreen is on, on every OS but macOS.
 */
@Mixin(Window.class)
public abstract class BorderlessWindowMixin {
    @Shadow @Final private long handle;
    @Shadow private boolean borderlessFullscreen;
    @Shadow protected abstract boolean setWindowSizeAndPosition(int x,int y,int width,int height);
    @Unique private long ladsMonitorCheck;
    @Inject(method="useBorderlessFullscreenWindow",at=@At("HEAD"),cancellable=true,require=1)
    private void ladsBorderlessWindow(CallbackInfoReturnable<Boolean> ci){if(com.thelads.core.client.BorderlessWindow.enabled())ci.setReturnValue(true);}
    @Inject(method="applyBorderlessFullscreenWindow",at=@At("RETURN"),require=1)
    private void ladsSyncBorderless(CallbackInfoReturnable<Boolean> ci){
        if(ci.getReturnValueZ())org.lwjgl.sdl.SDLVideo.SDL_SyncWindow(handle);
    }
    @Inject(method="updateFullscreenIfChanged",at=@At("TAIL"),require=1)
    private void ladsMonitorChanged(CallbackInfo ci){
        if(!borderlessFullscreen||System.nanoTime()-ladsMonitorCheck<500_000_000L)return;
        ladsMonitorCheck=System.nanoTime();
        try(var stack=org.lwjgl.system.MemoryStack.stackPush()){
            var rect=org.lwjgl.sdl.SDL_Rect.malloc(stack);
            int display=org.lwjgl.sdl.SDLVideo.SDL_GetDisplayForWindow(handle);
            if(!org.lwjgl.sdl.SDLVideo.SDL_GetDisplayBounds(display,rect))return;
            var window=(Window)(Object)this;
            if(window.getX()!=rect.x()||window.getY()!=rect.y()||window.getScreenWidth()!=rect.w()+1||window.getScreenHeight()!=rect.h()){
                setWindowSizeAndPosition(rect.x(),rect.y(),rect.w()+1,rect.h());
                org.lwjgl.sdl.SDLVideo.SDL_SyncWindow(handle);
            }
        }
    }

    @ModifyVariable(method = "setTitle", at = @At("HEAD"), argsOnly = true)
    private String ladsWindowTitle(String title) {
        if (title == null || title.isEmpty()) return com.thelads.core.LadsVersion.clientName();
        if (title.contains(" - ")) {
            return com.thelads.core.LadsVersion.clientName() + title.substring(title.indexOf(" - "));
        }
        return com.thelads.core.LadsVersion.clientName();
    }
}
