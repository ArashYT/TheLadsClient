package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.survivaltweaks.NativeSurvivalTweaks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * SurvivalTweaks: Keep Bars While Riding.
 * Keeps food and XP bars visible while riding a horse or mount.
 */
@Mixin(Hud.class)
public abstract class SurvivalKeepBarsMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow private Player getCameraPlayer() { throw new AssertionError(); }
    @Shadow private LivingEntity getPlayerVehicleWithHealth() { throw new AssertionError(); }
    @Shadow private int getVehicleMaxHearts(LivingEntity vehicle) { throw new AssertionError(); }
    @Shadow private boolean willPrioritizeJumpInfo() { throw new AssertionError(); }
    @Shadow private void extractFood(GuiGraphicsExtractor graphics, Player player, int y, int x) { throw new AssertionError(); }

    @ModifyVariable(method = "nextContextualInfoState", at = @At("STORE"), ordinal = 1)
    private boolean ladsKeepXpBarWhileRiding(boolean canShowVehicleJumpInfo) {
        if (NativeSurvivalTweaks.isKeepBarsWhileRidingEnabled()) {
            return canShowVehicleJumpInfo && this.willPrioritizeJumpInfo();
        }
        return canShowVehicleJumpInfo;
    }

    @Inject(method = "extractPlayerHealth", at = @At("TAIL"))
    private void ladsKeepFoodBarWhileRiding(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (!NativeSurvivalTweaks.isKeepBarsWhileRidingEnabled()) return;
        Player player = this.getCameraPlayer();
        if (player == null) return;
        LivingEntity vehicle = this.getPlayerVehicleWithHealth();
        int vehicleMaxHearts = this.getVehicleMaxHearts(vehicle);
        if (vehicleMaxHearts > 0 && this.minecraft.gameMode != null && this.minecraft.gameMode.canHurtPlayer()) {
            int rightX = graphics.guiWidth() / 2 + 91;
            int rightY = graphics.guiHeight() - 39;
            int rows = (int) Math.ceil(vehicleMaxHearts / 10.0);
            int foodY = rightY - rows * 10;
            this.extractFood(graphics, player, foodY, rightX);
        }
    }
}
