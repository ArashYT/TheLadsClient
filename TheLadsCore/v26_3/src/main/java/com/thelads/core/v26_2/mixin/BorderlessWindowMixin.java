package com.thelads.core.v26_2.mixin;
import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
/** Keep SDL's saved/restored window state, but use the exact monitor bounds without its extra pixel. */
@Mixin(Window.class)
public abstract class BorderlessWindowMixin {
    @Shadow @Final private long handle;
    @Shadow private boolean borderlessFullscreen;
    @Shadow protected abstract boolean setWindowSizeAndPosition(int x,int y,int width,int height);
    @Unique private long ladsMonitorCheck;
    @ModifyArg(method="applyBorderlessFullscreenWindow",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/platform/Window;setWindowSizeAndPosition(IIII)Z"),index=2,require=1)
    private int ladsExactMonitorWidth(int paddedWidth){return paddedWidth-1;}
    @Inject(method="applyBorderlessFullscreenWindow",at=@At("RETURN"),require=1)
    private void ladsSyncBorderless(CallbackInfoReturnable<Boolean> ci){
        if(ci.getReturnValueZ())org.lwjgl.sdl.SDLVideo.SDL_SyncWindow(handle);
    }
    @Inject(method="framebufferWidthPadding",at=@At("HEAD"),cancellable=true,require=1)
    private void ladsExactFramebuffer(CallbackInfoReturnable<Integer> ci){ci.setReturnValue(0);}
    @Inject(method="updateFullscreenIfChanged",at=@At("TAIL"),require=1)
    private void ladsMonitorChanged(CallbackInfo ci){
        if(!borderlessFullscreen||System.nanoTime()-ladsMonitorCheck<500_000_000L)return;
        ladsMonitorCheck=System.nanoTime();
        try(var stack=org.lwjgl.system.MemoryStack.stackPush()){
            var rect=org.lwjgl.sdl.SDL_Rect.malloc(stack);
            int display=org.lwjgl.sdl.SDLVideo.SDL_GetDisplayForWindow(handle);
            if(!org.lwjgl.sdl.SDLVideo.SDL_GetDisplayBounds(display,rect))return;
            var window=(Window)(Object)this;
            if(window.getX()!=rect.x()||window.getY()!=rect.y()||window.getScreenWidth()!=rect.w()||window.getScreenHeight()!=rect.h()){
                setWindowSizeAndPosition(rect.x(),rect.y(),rect.w(),rect.h());
                org.lwjgl.sdl.SDLVideo.SDL_SyncWindow(handle);
            }
        }
    }

    @ModifyVariable(method = "setTitle", at = @At("HEAD"), argsOnly = true)
    private String ladsWindowTitle(String title) {
        if (title == null || title.isEmpty()) return "The Lads Client 1.4.2";
        if (title.contains(" - ")) {
            return "The Lads Client 1.4.2" + title.substring(title.indexOf(" - "));
        }
        return "The Lads Client 1.4.2";
    }
}
