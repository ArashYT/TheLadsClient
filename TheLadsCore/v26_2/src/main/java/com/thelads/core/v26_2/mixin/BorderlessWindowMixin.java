package com.thelads.core.v26_2.mixin;
import com.mojang.blaze3d.platform.*;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Window.class)
public abstract class BorderlessWindowMixin {
    @Shadow @Final private long handle;
    @Shadow @Final private boolean exclusiveFullscreen;
    @Shadow @Final private MonitorManager monitorManager;
    @Shadow private boolean fullscreen;
    @Shadow private int windowedX,windowedY,windowedWidth,windowedHeight,x,y,width,height;
    @Unique private boolean ladsBorderless;
    @Inject(method="isSoftScreen",at=@At("HEAD"),cancellable=true,require=1)
    private void ladsExactFramebuffer(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> ci){
        // Our undecorated window already has exact monitor bounds, so do not subtract vanilla's extra pixel.
        if(!exclusiveFullscreen)ci.setReturnValue(false);
    }
    @Inject(method="setMode",at=@At("HEAD"),cancellable=true,require=1)
    private void ladsBorderless(CallbackInfo ci){
        if(exclusiveFullscreen)return;
        if(fullscreen){
            Monitor monitor=monitorManager.findBestMonitor((Window)(Object)this);
            if(monitor==null)return;
            if(!ladsBorderless){windowedX=x;windowedY=y;windowedWidth=width;windowedHeight=height;}
            var mode=GLFW.glfwGetVideoMode(monitor.monitor());if(mode==null)return;
            int[] xx={0},yy={0};GLFW.glfwGetMonitorPos(monitor.monitor(),xx,yy);
            GLFW.glfwRestoreWindow(handle);
            GLFW.glfwSetWindowAttrib(handle,GLFW.GLFW_DECORATED,GLFW.GLFW_FALSE);
            GLFW.glfwSetWindowMonitor(handle,0,xx[0],yy[0],mode.width(),mode.height(),GLFW.GLFW_DONT_CARE);
            x=xx[0];y=yy[0];width=mode.width();height=mode.height();ladsBorderless=true;ci.cancel();
        }else if(ladsBorderless){
            GLFW.glfwSetWindowAttrib(handle,GLFW.GLFW_DECORATED,GLFW.GLFW_TRUE);
            ladsBorderless=false; // Vanilla restores the saved bounds below.
        }
    }
}
