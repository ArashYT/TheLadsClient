package com.thelads.core.v26_2.mixin;
import com.thelads.core.v26_2.feature.NativeAutohide;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GuiTextRenderState.class)
public class AutohideTextMixin {
    @Shadow @Final @Mutable private int color;
    @Shadow @Final @Mutable private int backgroundColor;
    @Inject(method="<init>",at=@At("RETURN"),require=1)
    private void ladsTextOpacity(CallbackInfo ci){color=NativeAutohide.tint(color,NativeAutohide.scopeOpacity);backgroundColor=NativeAutohide.tint(backgroundColor,NativeAutohide.scopeOpacity);}
}
