package com.thelads.core.v1_21_11.mixin.chrome;
import com.thelads.core.v1_21_11.gui.SmoothScrollTarget;
import net.minecraft.client.gui.components.AbstractScrollArea;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(AbstractScrollArea.class)
public abstract class SmoothScrollMixin implements SmoothScrollTarget {
    @Shadow public abstract double scrollAmount();
    @Shadow public abstract void setScrollAmount(double amount);
    @Shadow public abstract int maxScrollAmount();
    @Unique private double ladsTarget;
    @Unique private long ladsFrame;
    @Unique private boolean ladsAnimating, ladsUpdating;
    @Redirect(method="mouseScrolled", at=@At(value="INVOKE", target="Lnet/minecraft/client/gui/components/AbstractScrollArea;setScrollAmount(D)V"), require=1)
    private void ladsWheel(AbstractScrollArea area, double amount) {
        if (!ladsAnimating) ladsTarget=scrollAmount();
        ladsTarget=Math.clamp(ladsTarget + amount-scrollAmount(),0,maxScrollAmount());
        ladsAnimating=true;
        if (ladsFrame==0) ladsFrame=System.nanoTime();
    }
    @Inject(method="setScrollAmount",at=@At("HEAD"),require=1)
    private void ladsDirectScroll(double amount,CallbackInfo ci){if(!ladsUpdating){ladsAnimating=false;ladsTarget=amount;}}
    @Override public void ladsAdvanceScroll() {
        long now=System.nanoTime();
        double dt=Math.clamp((now-ladsFrame)/1e9,0,.1); ladsFrame=now;
        if(!ladsAnimating)return;
        ladsTarget=Math.clamp(ladsTarget,0,maxScrollAmount());
        double next=scrollAmount()+(ladsTarget-scrollAmount())*(1-Math.exp(-dt*18));
        if(net.minecraft.client.Minecraft.getInstance().options.screenEffectScale().get()<=0||Math.abs(next-ladsTarget)<.2){next=ladsTarget;ladsAnimating=false;}
        ladsUpdating=true;
        try{setScrollAmount(next);}finally{ladsUpdating=false;}
    }
}
