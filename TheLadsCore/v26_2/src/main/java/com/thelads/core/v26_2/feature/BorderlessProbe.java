package com.thelads.core.v26_2.feature;

import com.thelads.core.client.BorderlessWindow;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** Opt-in QA through the actual native window: F11's path (toggleFullScreen, then the frame's updateFullscreenIfChanged). */
public final class BorderlessProbe {
    public static int run() throws Exception {
        var window=Minecraft.getInstance().getWindow();
        var module=NativeQualityOfLife.module("BorderlessFullscreen");boolean oldEnabled=module.isEnabled();
        boolean oldFull=window.isFullscreen();int passed=0;
        try{
            module.setEnabled(true);
            if(oldFull){window.toggleFullScreen();window.updateFullscreenIfChanged();}
            int width=window.getScreenWidth(),height=window.getScreenHeight();
            for(int i=0;i<2;i++){
                window.toggleFullScreen();window.updateFullscreenIfChanged();
                long handle=window.handle(),owner=GLFW.glfwGetWindowMonitor(handle);
                int[] px={0},py={0},pw={0},ph={0};GLFW.glfwGetWindowPos(handle,px,py);GLFW.glfwGetWindowSize(handle,pw,ph);
                long monitor=GLFW.glfwGetPrimaryMonitor();int[] mx={0},my={0};
                var monitors=GLFW.glfwGetMonitors();
                if(monitors!=null)for(int m=0;m<monitors.remaining();m++){int[] x={0},y={0};GLFW.glfwGetMonitorPos(monitors.get(m),x,y);if(x[0]==px[0]&&y[0]==py[0])monitor=monitors.get(m);}
                var mode=GLFW.glfwGetVideoMode(monitor);GLFW.glfwGetMonitorPos(monitor,mx,my);
                org.slf4j.LoggerFactory.getLogger("TheLadsCore").info("Lads borderless window: glfwGetWindowMonitor == {}, decorated={}, window rect x={} y={} w={} h={}, monitor rect x={} y={} w={} h={}, framebuffer {}x{}",
                    owner,GLFW.glfwGetWindowAttrib(handle,GLFW.GLFW_DECORATED),px[0],py[0],pw[0],ph[0],mx[0],my[0],mode.width(),mode.height(),window.getWidth(),window.getHeight());
                require(window.isFullscreen(),"fullscreen requested");passed++;
                require(owner==0,"borderless is a desktop window, not monitor-owned fullscreen");passed++;
                require(GLFW.glfwGetWindowAttrib(handle,GLFW.GLFW_DECORATED)==GLFW.GLFW_FALSE,"native window has no decorations");passed++;
                require(px[0]==mx[0]&&py[0]==my[0]&&pw[0]==mode.width()&&ph[0]==mode.height()+BorderlessWindow.EXTRA_HEIGHT,"borderless covers the monitor plus the capture-safe extra row");passed++;
                window.toggleFullScreen();window.updateFullscreenIfChanged();
                require(GLFW.glfwGetWindowAttrib(handle,GLFW.GLFW_DECORATED)==GLFW.GLFW_TRUE,"window decorations restored");passed++;
                require(window.getScreenWidth()==width&&window.getScreenHeight()==height,"windowed dimensions restored");passed++;
            }
            org.slf4j.LoggerFactory.getLogger("TheLadsCore").info("Lads borderless probe END: {} passed, 0 failed",passed);
            return passed;
        }finally{
            if(window.isFullscreen()!=oldFull){window.toggleFullScreen();window.updateFullscreenIfChanged();}
            module.setEnabled(oldEnabled);
        }
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
