package com.thelads.core.mixin.alwayson.skinlayers;

import com.thelads.core.features.alwayson.skinlayers.SkinLayersModBase;
import com.thelads.core.features.alwayson.skinlayers.SkinUtil;
import com.thelads.core.features.alwayson.skinlayers.accessor.ModelPartInjector;
import com.thelads.core.features.alwayson.skinlayers.accessor.PlayerEntityModelAccessor;
import com.thelads.core.features.alwayson.skinlayers.accessor.PlayerSettings;
import com.thelads.core.features.alwayson.skinlayers.accessor.AvatarRenderStateAccessor;
import com.thelads.core.features.alwayson.skinlayers.api.OffsetProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerCapeModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public class PlayerModelMixin<T extends LivingEntity> extends HumanoidModel<AvatarRenderState> implements PlayerEntityModelAccessor {
    @Shadow
    public ModelPart leftSleeve;
    @Shadow
    public ModelPart rightSleeve;
    @Shadow
    public ModelPart leftPants;
    @Shadow
    public ModelPart rightPants;
    @Shadow
    public ModelPart jacket;
    @Shadow
    private boolean slim;
    private boolean ignored;

    public PlayerModelMixin(ModelPart modelPart) {
        super(modelPart);
    }

    @Override
    public boolean hasThinArms() {
        return this.slim;
    }

    @Inject(method = "setupAnim", at = @At("TAIL"), cancellable = true)
    public void setupAnim(AvatarRenderState playerRenderState, CallbackInfo ci) {
        if (this.ignored) {
            return;
        }
        if (((Object) this) instanceof PlayerCapeModel || !(playerRenderState instanceof AvatarRenderStateAccessor)) {
            return;
        }
        AbstractClientPlayer abstractClientPlayer = ((AvatarRenderStateAccessor) playerRenderState).skinlayers$getPlayer();
        if (abstractClientPlayer == null) {
            return;
        }
        PlayerSettings settings = (PlayerSettings) abstractClientPlayer;
        ((ModelPartInjector) (Object) this.hat).setInjectedMesh(null, null);
        ((ModelPartInjector) (Object) this.jacket).setInjectedMesh(null, null);
        ((ModelPartInjector) (Object) this.leftSleeve).setInjectedMesh(null, null);
        ((ModelPartInjector) (Object) this.rightSleeve).setInjectedMesh(null, null);
        ((ModelPartInjector) (Object) this.leftPants).setInjectedMesh(null, null);
        ((ModelPartInjector) (Object) this.rightPants).setInjectedMesh(null, null);
        boolean inGui = Minecraft.getInstance().gui.screen() != null;
        if (!inGui && (Minecraft.getInstance().player == null || abstractClientPlayer.distanceToSqr(Minecraft.getInstance().gameRenderer.mainCamera().position()) > (double) (SkinLayersModBase.config.renderDistanceLOD * SkinLayersModBase.config.renderDistanceLOD))) {
            return;
        }
        if (!SkinUtil.setup3dLayers(abstractClientPlayer, settings, this.slim)) {
            return;
        }
        ItemStack itemStack = abstractClientPlayer.getItemBySlot(EquipmentSlot.HEAD);
        if (SkinLayersModBase.config.enableHat && (itemStack == null || !SkinLayersModBase.hideHeadLayers.contains(itemStack.getItem()))) {
            ((ModelPartInjector) (Object) this.hat).setInjectedMesh(settings.getHeadMesh(), OffsetProvider.HEAD);
        }
        if (SkinLayersModBase.config.enableJacket) {
            ((ModelPartInjector) (Object) this.jacket).setInjectedMesh(settings.getTorsoMesh(), OffsetProvider.BODY);
        }
        if (SkinLayersModBase.config.enableLeftSleeve) {
            ((ModelPartInjector) (Object) this.leftSleeve).setInjectedMesh(settings.getLeftArmMesh(), this.slim ? OffsetProvider.LEFT_ARM_SLIM : OffsetProvider.LEFT_ARM);
        }
        if (SkinLayersModBase.config.enableRightSleeve) {
            ((ModelPartInjector) (Object) this.rightSleeve).setInjectedMesh(settings.getRightArmMesh(), this.slim ? OffsetProvider.RIGHT_ARM_SLIM : OffsetProvider.RIGHT_ARM);
        }
        if (SkinLayersModBase.config.enableLeftPants) {
            ((ModelPartInjector) (Object) this.leftPants).setInjectedMesh(settings.getLeftLegMesh(), OffsetProvider.LEFT_LEG);
        }
        if (SkinLayersModBase.config.enableRightPants) {
            ((ModelPartInjector) (Object) this.rightPants).setInjectedMesh(settings.getRightLegMesh(), OffsetProvider.RIGHT_LEG);
        }
    }

    @Override
    public void setIgnored(boolean ignored) {
        this.ignored = ignored;
    }
}
