package com.thelads.core.v26_2.mixin;
import com.thelads.core.v26_2.feature.*;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GuiItemRenderState.class)
public class AutohideItemMixin implements FadedItem {
    @Unique private float ladsAlpha=1;
    @Inject(method="<init>",at=@At("RETURN"),require=1)
    private void ladsCapture(CallbackInfo ci){ladsAlpha=NativeAutohide.scopeOpacity;}
    public float ladsOpacity(){return ladsAlpha;}
}
