package com.thelads.core.v26_2.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import com.thelads.core.v26_2.feature.LegacySwing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public class LegacySwingMixin {
    @Inject(method="swingArm",at=@At("HEAD"),cancellable=true,require=1)
    private void ladsConsoleSwing(float progress,PoseStack pose,int side,HumanoidArm arm,CallbackInfo ci){
        if(LegacySwing.apply(pose,progress,side))ci.cancel();
    }
}
