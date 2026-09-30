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
    private void ladsFadeItem(GuiRenderState state,BlitRenderState b){state.addBlitToCurrentLayer(new BlitRenderState(b.pipeline(),b.textureSetup(),b.pose(),b.x0(),b.y0(),b.x1(),b.y1(),b.u0(),b.u1(),b.v0(),b.v1(),NativeAutohide.tint(b.color(),ladsItemAlpha),b.scissorArea(),b.bounds()));}
}
