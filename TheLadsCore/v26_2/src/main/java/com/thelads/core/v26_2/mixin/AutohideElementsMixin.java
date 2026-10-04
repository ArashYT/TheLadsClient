package com.thelads.core.v26_2.mixin;
import com.thelads.core.v26_2.feature.*;
import net.minecraft.client.renderer.state.gui.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GuiRenderState.class)
public class AutohideElementsMixin {
    @ModifyVariable(method="addGuiElement",at=@At("HEAD"),argsOnly=true,require=1)
    private GuiElementRenderState ladsFade(GuiElementRenderState element){return NativeAutohide.scopeOpacity<1?new FadedElement(element,NativeAutohide.scopeOpacity):element;}
    @Inject(method={"addGuiElement","addItem","addText","addPicturesInPictureState"},at=@At("HEAD"),cancellable=true,require=1)
    private void ladsFullyHidden(CallbackInfo ci){if(NativeAutohide.scopeOpacity==0)ci.cancel();}
    /** A faded picture (Xaero's minimap, the paper doll at its Model Opacity) keeps its opacity for the blit (AutohidePictureMixin). */
    @Inject(method="addPicturesInPictureState",at=@At("HEAD"),require=1)
    private void ladsFadePicture(net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState state,CallbackInfo ci){
        if(NativeAutohide.scopeOpacity>0&&NativeAutohide.scopeOpacity<1)NativeAutohide.PICTURES.put(state,NativeAutohide.scopeOpacity);
    }
}
