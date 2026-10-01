package com.thelads.core.v26_2.feature;

import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLVideo;

/** Opt-in QA through the actual native window, not a simulated window model. */
public final class BorderlessProbe {
    public static int run() throws Exception {
        var window=Minecraft.getInstance().getWindow();
        var type=window.getClass();
        var requested=type.getDeclaredField("fullscreenRequested");requested.setAccessible(true);
        var exclusive=type.getDeclaredField("exclusiveFullscreen");exclusive.setAccessible(true);
        var borderless=type.getDeclaredField("borderlessFullscreen");borderless.setAccessible(true);
        var module=NativeQualityOfLife.module("BorderlessFullscreen");boolean oldEnabled=module.isEnabled();
        boolean oldFull=requested.getBoolean(window),oldExclusive=exclusive.getBoolean(window);
        int width=window.getScreenWidth(),height=window.getScreenHeight(),passed=0;
        try{
            module.setEnabled(true);
            window.setFullscreen(false);window.updateFullscreenIfChanged();
            // Even with vanilla's Exclusive Fullscreen option on, the module keeps fullscreen a borderless desktop window.
            window.setExclusiveFullscreen(true);
            width=window.getScreenWidth();height=window.getScreenHeight();
            for(int i=0;i<2;i++){
                window.setFullscreen(true);window.updateFullscreenIfChanged();
                long flags=SDLVideo.SDL_GetWindowFlags(window.handle());
                try(var stack=org.lwjgl.system.MemoryStack.stackPush()){
                    var rect=org.lwjgl.sdl.SDL_Rect.malloc(stack);int display=SDLVideo.SDL_GetDisplayForWindow(window.handle());
                    require(SDLVideo.SDL_GetDisplayBounds(display,rect),"monitor bounds available");
                    var x=stack.mallocInt(1);var y=stack.mallocInt(1);var w=stack.mallocInt(1);var h=stack.mallocInt(1);
                    SDLVideo.SDL_GetWindowPosition(window.handle(),x,y);SDLVideo.SDL_GetWindowSize(window.handle(),w,h);
                    org.slf4j.LoggerFactory.getLogger("TheLadsCore").info("Lads borderless window: SDL fullscreen flag={} (SDL equivalent of glfwGetWindowMonitor == 0 when false), borderless={}, window rect x={} y={} w={} h={}, monitor rect x={} y={} w={} h={}, framebuffer {}x{}",
                        (flags&SDLVideo.SDL_WINDOW_FULLSCREEN)!=0,(flags&SDLVideo.SDL_WINDOW_BORDERLESS)!=0,x.get(0),y.get(0),w.get(0),h.get(0),rect.x(),rect.y(),rect.w(),rect.h(),window.getWidth(),window.getHeight());
                    require(borderless.getBoolean(window),"native borderless window path is active");passed++;
                    require((flags&SDLVideo.SDL_WINDOW_FULLSCREEN)==0,"borderless is a desktop window, not SDL/monitor fullscreen");passed++;
                    require((flags&SDLVideo.SDL_WINDOW_BORDERLESS)!=0,"native window has no decorations");passed++;
                    // Mojang's capture-safe pad: one column wider than the display; the framebuffer still matches the display.
                    require(x.get(0)==rect.x()&&y.get(0)==rect.y()&&w.get(0)==rect.w()+1&&h.get(0)==rect.h()&&window.getWidth()==rect.w(),
                        "borderless covers the display plus the capture-safe extra column");passed++;
                }
                require(!window.isExclusiveFullscreen(),"fullscreen does not take exclusive display ownership");passed++;
                window.setFullscreen(false);window.updateFullscreenIfChanged();
                require(!borderless.getBoolean(window),"leaving fullscreen clears borderless state");passed++;
                require(window.getScreenWidth()==width&&window.getScreenHeight()==height,"windowed dimensions are restored");passed++;
            }
            org.slf4j.LoggerFactory.getLogger("TheLadsCore").info("Lads borderless probe END: {} passed, 0 failed",passed);
            return passed;
        }finally{module.setEnabled(oldEnabled);window.setExclusiveFullscreen(oldExclusive);window.setFullscreen(oldFull);window.updateFullscreenIfChanged();}
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
