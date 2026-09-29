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
        boolean oldFull=requested.getBoolean(window),oldExclusive=exclusive.getBoolean(window);
        int width=window.getScreenWidth(),height=window.getScreenHeight(),passed=0;
        try{
            window.setFullscreen(false);window.updateFullscreenIfChanged();window.setExclusiveFullscreen(false);
            width=window.getScreenWidth();height=window.getScreenHeight();
            for(int i=0;i<2;i++){
                window.setFullscreen(true);window.updateFullscreenIfChanged();
                require(borderless.getBoolean(window),"native borderless path is active");passed++;
                require((SDLVideo.SDL_GetWindowFlags(window.handle())&SDLVideo.SDL_WINDOW_BORDERLESS)!=0,"native window has no decorations");passed++;
                require(!window.isExclusiveFullscreen(),"fullscreen does not take exclusive display ownership");passed++;
                window.setFullscreen(false);window.updateFullscreenIfChanged();
                require(!borderless.getBoolean(window),"leaving fullscreen clears borderless state");passed++;
                require(window.getScreenWidth()==width&&window.getScreenHeight()==height,"windowed dimensions are restored");passed++;
            }
            org.slf4j.LoggerFactory.getLogger("TheLadsCore").info("Lads borderless probe END: {} passed, 0 failed",passed);
            return passed;
        }finally{window.setExclusiveFullscreen(oldExclusive);window.setFullscreen(oldFull);window.updateFullscreenIfChanged();}
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
