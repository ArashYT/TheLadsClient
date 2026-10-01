package com.thelads.core.v26_2.mixin;
import com.mojang.blaze3d.platform.*;
import com.thelads.core.client.BorderlessWindow;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
/** BorderlessFullscreen: fullscreen is an undecorated desktop window (no GLFW monitor) covering the current monitor. */
@Mixin(Window.class)
public abstract class BorderlessWindowMixin {
    @Shadow @Final private long handle;
    @Shadow @Final private MonitorManager monitorManager;
    @Shadow private boolean fullscreen;
    @Shadow private int windowedX,windowedY,windowedWidth,windowedHeight,x,y,width,height;
    @Shadow protected abstract void refreshFramebufferSize();
    @Unique private boolean ladsBorderless,ladsWasMaximized;
    @Unique private long ladsLastMonitorCheck;
    /** Vanilla's Windows soft fullscreen hides a framebuffer row; the Lads window shows its whole framebuffer. */
    @Inject(method="isSoftScreen",at=@At("HEAD"),cancellable=true,require=1)
    private void ladsWholeFramebuffer(CallbackInfoReturnable<Boolean> ci){if(ladsBorderless)ci.setReturnValue(false);}
    @Inject(method="setMode",at=@At("HEAD"),cancellable=true,require=1)
    private void ladsMode(CallbackInfo ci){
        if(fullscreen&&(ladsBorderless||BorderlessWindow.enabled())){
            Monitor monitor=monitorManager.findBestMonitor((Window)(Object)this);
            if(monitor==null)return;
            if(!ladsBorderless&&GLFW.glfwGetWindowMonitor(handle)==0){
                // From a desktop window: remember it. A window created fullscreen keeps the size vanilla saved at startup.
                ladsWasMaximized=GLFW.glfwGetWindowAttrib(handle,GLFW.GLFW_MAXIMIZED)==GLFW.GLFW_TRUE;
                if(ladsWasMaximized)GLFW.glfwRestoreWindow(handle);
                int[] px={0},py={0},pw={0},ph={0};GLFW.glfwGetWindowPos(handle,px,py);GLFW.glfwGetWindowSize(handle,pw,ph);
                windowedX=px[0];windowedY=py[0];windowedWidth=pw[0];windowedHeight=ph[0];
            }
            ladsBorderless=true;ladsCover(monitor.monitor());ci.cancel();
        }else if(!fullscreen&&ladsBorderless){
            ladsBorderless=false;
            GLFW.glfwSetWindowAttrib(handle,GLFW.GLFW_DECORATED,GLFW.GLFW_TRUE);
            GLFW.glfwSetWindowMonitor(handle,0,windowedX,windowedY,windowedWidth,windowedHeight,GLFW.GLFW_DONT_CARE);
            x=windowedX;y=windowedY;width=windowedWidth;height=windowedHeight;
            if(ladsWasMaximized)GLFW.glfwMaximizeWindow(handle);
            refreshFramebufferSize();ci.cancel();
        }
    }
    @Unique private void ladsCover(long monitor){
        var mode=GLFW.glfwGetVideoMode(monitor);if(mode==null)return;
        int[] mx={0},my={0};GLFW.glfwGetMonitorPos(monitor,mx,my);
        GLFW.glfwSetWindowAttrib(handle,GLFW.GLFW_DECORATED,GLFW.GLFW_FALSE);
        GLFW.glfwSetWindowMonitor(handle,0,mx[0],my[0],mode.width(),mode.height()+BorderlessWindow.EXTRA_HEIGHT,GLFW.GLFW_DONT_CARE);
        x=mx[0];y=my[0];width=mode.width();height=mode.height()+BorderlessWindow.EXTRA_HEIGHT;refreshFramebufferSize();
    }
    /** Follows a resolution change of the monitor while borderless. */
    @Inject(method="updateFullscreenIfChanged",at=@At("TAIL"),require=1)
    private void ladsTrackMonitor(CallbackInfo ci){
        if(!ladsBorderless||!fullscreen||System.nanoTime()-ladsLastMonitorCheck<500_000_000L)return;
        ladsLastMonitorCheck=System.nanoTime();
        var monitor=monitorManager.findBestMonitor((Window)(Object)this);if(monitor==null)return;
        var mode=GLFW.glfwGetVideoMode(monitor.monitor());if(mode==null)return;
        int[] mx={0},my={0};GLFW.glfwGetMonitorPos(monitor.monitor(),mx,my);
        if(x!=mx[0]||y!=my[0]||width!=mode.width()||height!=mode.height()+BorderlessWindow.EXTRA_HEIGHT)ladsCover(monitor.monitor());
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
