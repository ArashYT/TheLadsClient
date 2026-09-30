package com.thelads.core.v26_2.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.v26_2.feature.NativeAutohide;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.state.gui.*;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** A picture submitted inside a faded Autohide scope (Xaero's minimap) is blitted at that opacity (1.4.0; it did not fade before). */
@Mixin(PictureInPictureRenderer.class)
public class AutohidePictureMixin {
    @Unique private float ladsPictureAlpha=1;
    @Inject(method="blitTexture",at=@At("HEAD"),require=1)
    private void ladsPicture(PictureInPictureRenderState state,GuiRenderState target,CallbackInfo ci){Float alpha=NativeAutohide.PICTURES.remove(state);ladsPictureAlpha=alpha==null?1:alpha;}
    @WrapOperation(method="blitTexture",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/state/gui/GuiRenderState;addBlitToCurrentLayer(Lnet/minecraft/client/renderer/state/gui/BlitRenderState;)V"),require=1)
    private void ladsFadePicture(GuiRenderState state,BlitRenderState blit,Operation<Void> original){original.call(state,ladsPictureAlpha<1?NativeAutohide.fade(blit,ladsPictureAlpha):blit);}
}
