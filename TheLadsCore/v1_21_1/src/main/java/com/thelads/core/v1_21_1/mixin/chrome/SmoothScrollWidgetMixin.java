package com.thelads.core.v1_21_1.mixin.chrome;
import com.thelads.core.v1_21_1.gui.SmoothScrollTarget;
import net.minecraft.client.gui.components.AbstractScrollWidget;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** The same smooth scrolling for the 1.21.1 text areas (MultiLineEditBox, scrolling text), also AbstractScrollArea on 26.x. */
@Mixin(AbstractScrollWidget.class)
public abstract class SmoothScrollWidgetMixin implements SmoothScrollTarget {
    @Shadow protected abstract double scrollAmount();
    @Shadow protected abstract void setScrollAmount(double amount);
    @Shadow protected abstract int getMaxScrollAmount();
    @Unique private double ladsTarget;
    @Unique private long ladsFrame;
    @Unique private boolean ladsAnimating, ladsUpdating;
    @Redirect(method="mouseScrolled", at=@At(value="INVOKE", target="Lnet/minecraft/client/gui/components/AbstractScrollWidget;setScrollAmount(D)V"), require=1)
    private void ladsWheel(AbstractScrollWidget widget, double amount) {
        if (!ladsAnimating) ladsTarget=scrollAmount();
        ladsTarget=Math.clamp(ladsTarget + amount-scrollAmount(),0,getMaxScrollAmount());
        ladsAnimating=true;
        if (ladsFrame==0) ladsFrame=System.nanoTime();
    }
    @Inject(method="setScrollAmount",at=@At("HEAD"),require=1)
    private void ladsDirectScroll(double amount,CallbackInfo ci){if(!ladsUpdating){ladsAnimating=false;ladsTarget=amount;}}
    @Override public void ladsAdvanceScroll() {
        long now=System.nanoTime();
        double dt=Math.clamp((now-ladsFrame)/1e9,0,.1); ladsFrame=now;
        if(!ladsAnimating)return;
        ladsTarget=Math.clamp(ladsTarget,0,getMaxScrollAmount());
        double next=scrollAmount()+(ladsTarget-scrollAmount())*(1-Math.exp(-dt*18));
        if(net.minecraft.client.Minecraft.getInstance().options.screenEffectScale().get()<=0||Math.abs(next-ladsTarget)<.2){next=ladsTarget;ladsAnimating=false;}
        ladsUpdating=true;
        try{setScrollAmount(next);}finally{ladsUpdating=false;}
    }
}
