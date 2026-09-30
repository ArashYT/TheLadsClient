package com.thelads.core.v26_2.mixin;
import com.thelads.core.v26_2.feature.*;
import net.minecraft.client.gui.render.*;
import net.minecraft.client.renderer.state.gui.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GuiRenderer.class)
public class AutohideItemRenderMixin {
    @Unique private float ladsItemAlpha=1;
    @Inject(method="submitBlitFromItemAtlas",at=@At("HEAD"),require=1)
    private void ladsItem(GuiItemRenderState item,GuiItemAtlas.SlotView slot,CallbackInfo ci){ladsItemAlpha=((FadedItem)(Object)item).ladsOpacity();}
    @Redirect(method="submitBlitFromItemAtlas",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/state/gui/GuiRenderState;addBlitToCurrentLayer(Lnet/minecraft/client/renderer/state/gui/BlitRenderState;)V"),require=1)
    // The item atlas is premultiplied: scaling only alpha brightened fading items (1.4.0 fix, as on 1.21.x).
    private void ladsFadeItem(GuiRenderState state,BlitRenderState b){state.addBlitToCurrentLayer(ladsItemAlpha<1?NativeAutohide.fade(b,ladsItemAlpha):b);}
}
