package com.thelads.core.v26_2.mixin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.gui.screens.options.SkinCustomizationScreen;
import net.minecraft.client.model.Model;
import net.minecraft.world.entity.player.PlayerModelPart;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(PlayerSkinWidget.class)
public class SkinPreviewMixin {
    @Shadow @Final private Model.Simple wideModel;
    @Shadow @Final private Model.Simple slimModel;
    @Shadow @Final private java.util.function.Supplier<net.minecraft.world.entity.player.PlayerSkin> skin;
    @ModifyArg(method="extractWidgetRenderState",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/GuiGraphicsExtractor;skin(Lnet/minecraft/client/model/Model$Simple;Lnet/minecraft/resources/Identifier;FFFFIIII)V"),index=2,require=1)
    private float ladsFitPreview(float scale){return Math.min(scale,((PlayerSkinWidget)(Object)this).getWidth()/1.8f);}
    @Inject(method="extractWidgetRenderState",at=@At("HEAD"),require=1)
    private void ladsPreviewLayers(CallbackInfo ci){
        var mc=Minecraft.getInstance();
        if(!(mc.gui.screen() instanceof SkinCustomizationScreen)&&!(mc.gui.screen() instanceof com.thelads.core.v26_2.gui.SkinChangerScreen))return;
        for(var model:new Model.Simple[]{wideModel,slimModel}){
            com.thelads.core.v26_2.feature.PreviewSkinLayers.apply(model,skin.get());
            var root=model.root();
            root.getChild("head").getChild("hat").visible=mc.options.isModelPartEnabled(PlayerModelPart.HAT);
            root.getChild("body").getChild("jacket").visible=mc.options.isModelPartEnabled(PlayerModelPart.JACKET);
            root.getChild("left_arm").getChild("left_sleeve").visible=mc.options.isModelPartEnabled(PlayerModelPart.LEFT_SLEEVE);
            root.getChild("right_arm").getChild("right_sleeve").visible=mc.options.isModelPartEnabled(PlayerModelPart.RIGHT_SLEEVE);
            root.getChild("left_leg").getChild("left_pants").visible=mc.options.isModelPartEnabled(PlayerModelPart.LEFT_PANTS_LEG);
            root.getChild("right_leg").getChild("right_pants").visible=mc.options.isModelPartEnabled(PlayerModelPart.RIGHT_PANTS_LEG);
        }
    }
}
