package com.thelads.core.v1_20_1.game.mixin;

import com.thelads.core.modules.OldAnimationsModule;
import com.thelads.core.v1_20_1.game.anim.NativeOldAnimations1201;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public abstract class OldAnimationsPlayerMixin {
    // 1.7 Animations' third person: the sword block's arm poses (the main arm blocks, the shield arm is empty).
    @Inject(method = "setModelProperties", at = @At("TAIL"))
    private void lads$oldThirdPerson(AbstractClientPlayer player, CallbackInfo callback) {
        if (player.isSpectator() || !NativeOldAnimations1201.active(OldAnimationsModule.Feature.THIRD_PERSON)
            || !NativeOldAnimations1201.swordBlocking(player)) return;
        PlayerModel<AbstractClientPlayer> model = ((PlayerRenderer) (Object) this).getModel();
        boolean right = player.getMainArm() == HumanoidArm.RIGHT;
        model.rightArmPose = right ? HumanoidModel.ArmPose.BLOCK : HumanoidModel.ArmPose.EMPTY;
        model.leftArmPose = right ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.BLOCK;
    }
}
