package com.thelads.core.v26_2.mixin;
import com.mojang.blaze3d.platform.*;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(Window.class)
public abstract class BorderlessWindowMixin {
    @Shadow @Final private long handle;
    @Shadow @Final private boolean exclusiveFullscreen;
    @Shadow @Final private MonitorManager monitorManager;
    @Shadow private boolean fullscreen;
    @Shadow private int windowedX,windowedY,windowedWidth,windowedHeight,x,y,width,height;
    @Shadow protected abstract void refreshFramebufferSize();
    @Unique private boolean ladsBorderless,ladsWasMaximized;
    @Unique private long ladsLastMonitorCheck;
    @Inject(method="isSoftScreen",at=@At("HEAD"),cancellable=true,require=1)
    private void ladsExactFramebuffer(CallbackInfoReturnable<Boolean> ci){if(!exclusiveFullscreen)ci.setReturnValue(false);}
    @Inject(method="setMode",at=@At("HEAD"),cancellable=true,require=1)
    private void ladsMode(CallbackInfo ci){
        if(exclusiveFullscreen)return;
        if(fullscreen){
            Monitor monitor=monitorManager.findBestMonitor((Window)(Object)this);
            if(monitor==null)return;
            var mode=GLFW.glfwGetVideoMode(monitor.monitor());if(mode==null)return;
            if(!ladsBorderless){
                ladsWasMaximized=GLFW.glfwGetWindowAttrib(handle,GLFW.GLFW_MAXIMIZED)==GLFW.GLFW_TRUE;
                if(ladsWasMaximized)GLFW.glfwRestoreWindow(handle);
                int[] px={0},py={0},pw={0},ph={0};GLFW.glfwGetWindowPos(handle,px,py);GLFW.glfwGetWindowSize(handle,pw,ph);
                windowedX=px[0];windowedY=py[0];windowedWidth=pw[0];windowedHeight=ph[0];
            }
            ladsBorderless=true;ladsApplyMonitor(monitor.monitor());ci.cancel();
        }else if(ladsBorderless){
            ladsBorderless=false;
            GLFW.glfwSetWindowAttrib(handle,GLFW.GLFW_DECORATED,GLFW.GLFW_TRUE);
            GLFW.glfwSetWindowMonitor(handle,0,windowedX,windowedY,windowedWidth,windowedHeight,GLFW.GLFW_DONT_CARE);
            x=windowedX;y=windowedY;width=windowedWidth;height=windowedHeight;
            if(ladsWasMaximized)GLFW.glfwMaximizeWindow(handle);
            refreshFramebufferSize();ci.cancel();
        }
    }
    @Unique private void ladsApplyMonitor(long monitor){
        var mode=GLFW.glfwGetVideoMode(monitor);if(mode==null)return;
        int[] xx={0},yy={0};GLFW.glfwGetMonitorPos(monitor,xx,yy);
        GLFW.glfwSetWindowAttrib(handle,GLFW.GLFW_DECORATED,GLFW.GLFW_FALSE);
        GLFW.glfwSetWindowMonitor(handle,0,xx[0],yy[0],mode.width(),mode.height(),GLFW.GLFW_DONT_CARE);
        x=xx[0];y=yy[0];width=mode.width();height=mode.height();refreshFramebufferSize();
    }
    @Inject(method="updateFullscreenIfChanged",at=@At("TAIL"),require=1)
    private void ladsTrackMonitor(CallbackInfo ci){
        if(!ladsBorderless||!fullscreen||System.nanoTime()-ladsLastMonitorCheck<500_000_000L)return;
        ladsLastMonitorCheck=System.nanoTime();
        var monitor=monitorManager.findBestMonitor((Window)(Object)this);if(monitor==null)return;
        var mode=GLFW.glfwGetVideoMode(monitor.monitor());if(mode==null)return;
        int[] xx={0},yy={0};GLFW.glfwGetMonitorPos(monitor.monitor(),xx,yy);
        if(x!=xx[0]||y!=yy[0]||width!=mode.width()||height!=mode.height())ladsApplyMonitor(monitor.monitor());
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
