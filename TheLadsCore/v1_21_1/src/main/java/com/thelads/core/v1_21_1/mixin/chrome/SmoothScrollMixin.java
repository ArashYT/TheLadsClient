package com.thelads.core.v1_21_1.mixin.chrome;
import com.thelads.core.v1_21_1.gui.SmoothScrollTarget;
import net.minecraft.client.gui.components.AbstractSelectionList;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** 26.x AbstractScrollArea smooth scrolling for the 1.21.1 selection lists (AbstractScrollArea arrived in 1.21.4). */
@Mixin(AbstractSelectionList.class)
public abstract class SmoothScrollMixin implements SmoothScrollTarget {
    @Shadow public abstract double getScrollAmount();
    @Shadow public abstract void setScrollAmount(double amount);
    @Shadow public abstract int getMaxScroll();
    @Unique private double ladsTarget;
    @Unique private long ladsFrame;
    @Unique private boolean ladsAnimating, ladsUpdating;
    @Redirect(method="mouseScrolled", at=@At(value="INVOKE", target="Lnet/minecraft/client/gui/components/AbstractSelectionList;setScrollAmount(D)V"), require=1)
    private void ladsWheel(AbstractSelectionList<?> list, double amount) {
        if (!ladsAnimating) ladsTarget=getScrollAmount();
        ladsTarget=Math.clamp(ladsTarget + amount-getScrollAmount(),0,getMaxScroll());
        ladsAnimating=true;
        if (ladsFrame==0) ladsFrame=System.nanoTime();
    }
    @Inject(method="setScrollAmount",at=@At("HEAD"),require=1)
    private void ladsDirectScroll(double amount,CallbackInfo ci){if(!ladsUpdating){ladsAnimating=false;ladsTarget=amount;}}
    @Override public void ladsAdvanceScroll() {
        long now=System.nanoTime();
        double dt=Math.clamp((now-ladsFrame)/1e9,0,.1); ladsFrame=now;
        if(!ladsAnimating)return;
        ladsTarget=Math.clamp(ladsTarget,0,getMaxScroll());
        double next=getScrollAmount()+(ladsTarget-getScrollAmount())*(1-Math.exp(-dt*18));
        if(net.minecraft.client.Minecraft.getInstance().options.screenEffectScale().get()<=0||Math.abs(next-ladsTarget)<.2){next=ladsTarget;ladsAnimating=false;}
        ladsUpdating=true;
        try{setScrollAmount(next);}finally{ladsUpdating=false;}
    }
}
