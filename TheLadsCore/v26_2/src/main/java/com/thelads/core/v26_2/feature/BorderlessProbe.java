package com.thelads.core.v26_2.feature;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** Opt-in QA through the actual native window. */
public final class BorderlessProbe {
    public static int run() throws Exception {
        var window=Minecraft.getInstance().getWindow();
        var method=window.getClass().getDeclaredMethod("setMode");method.setAccessible(true);
        boolean oldFull=window.isFullscreen();int passed=0;
        try{
            if(oldFull){window.toggleFullScreen();method.invoke(window);}
            int width=window.getScreenWidth(),height=window.getScreenHeight();
            for(int i=0;i<2;i++){
                window.toggleFullScreen();method.invoke(window);
                require(window.isFullscreen(),"fullscreen requested");passed++;
                require(GLFW.glfwGetWindowMonitor(window.handle())==0,"borderless does not own the display");passed++;
                require(GLFW.glfwGetWindowAttrib(window.handle(),GLFW.GLFW_DECORATED)==GLFW.GLFW_FALSE,"native window has no decorations");passed++;
                window.toggleFullScreen();method.invoke(window);
                require(GLFW.glfwGetWindowAttrib(window.handle(),GLFW.GLFW_DECORATED)==GLFW.GLFW_TRUE,"window decorations restored");passed++;
                require(window.getScreenWidth()==width&&window.getScreenHeight()==height,"windowed dimensions restored");passed++;
            }
            org.slf4j.LoggerFactory.getLogger("TheLadsCore").info("Lads borderless probe END: {} passed, 0 failed",passed);
            return passed;
        }finally{if(window.isFullscreen()!=oldFull){window.toggleFullScreen();method.invoke(window);}}
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
