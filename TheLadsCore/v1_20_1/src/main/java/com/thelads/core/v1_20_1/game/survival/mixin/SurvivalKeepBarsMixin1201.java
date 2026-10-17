package com.thelads.core.v1_20_1.game.survival.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.v1_20_1.game.survival.NativeSurvivalTweaks1201;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * SurvivalTweaks: Keep Bars While Riding. The food bar stays (above the mount's hearts) and the experience bar stays except
 * while a horse jump is charging, as 26.3's SurvivalKeepBarsMixin does on its HUD.
 */
@Mixin(Gui.class)
public abstract class SurvivalKeepBarsMixin1201 {
    @Unique private static final ResourceLocation LADS_ICONS = new ResourceLocation("textures/gui/icons.png");
    @Shadow @Final private RandomSource random;
    @Shadow private int tickCount;
    @Shadow private int screenWidth;
    @Shadow private int screenHeight;
    @Shadow private Player getCameraPlayer() { throw new AssertionError(); }
    @Shadow private LivingEntity getPlayerVehicleWithHealth() { throw new AssertionError(); }
    @Shadow private int getVehicleMaxHearts(LivingEntity vehicle) { throw new AssertionError(); }
    @Shadow private int getVisibleVehicleHeartRows(int hearts) { throw new AssertionError(); }

    /** Riding a jumping mount swaps the experience bar for the jump meter; here the meter shows only while a jump is charging. */
    @WrapOperation(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;jumpableVehicle()Lnet/minecraft/world/entity/PlayerRideableJumping;")
    )
    private PlayerRideableJumping lads$keepXpBarWhileRiding(LocalPlayer player, Operation<PlayerRideableJumping> original) {
        PlayerRideableJumping vehicle = original.call(player);
        if (vehicle != null && NativeSurvivalTweaks1201.isKeepBarsWhileRidingEnabled() && player.getJumpRidingScale() <= 0.0F) {
            return null;
        }
        return vehicle;
    }

    /** Vanilla leaves the food bar out while the player rides a mount with hearts (they take its place); drawn here above those hearts. */
    @Inject(method = "renderPlayerHealth", at = @At("TAIL"))
    private void lads$keepFoodBarWhileRiding(GuiGraphics graphics, CallbackInfo ci) {
        if (!NativeSurvivalTweaks1201.isKeepBarsWhileRidingEnabled()) return;
        Player player = this.getCameraPlayer();
        if (player == null) return;
        int vehicleMaxHearts = this.getVehicleMaxHearts(this.getPlayerVehicleWithHealth());
        if (vehicleMaxHearts <= 0) return;
        int foodLevel = player.getFoodData().getFoodLevel();
        int rightX = this.screenWidth / 2 + 91;
        int baseY = this.screenHeight - 39 - this.getVisibleVehicleHeartRows(vehicleMaxHearts) * 10;
        boolean hungry = player.hasEffect(MobEffects.HUNGER);
        for (int i = 0; i < 10; i++) {
            int y = baseY;
            if (player.getFoodData().getSaturationLevel() <= 0.0F && this.tickCount % (foodLevel * 3 + 1) == 0) {
                y += this.random.nextInt(3) - 1;
            }
            int x = rightX - i * 8 - 9;
            graphics.blit(LADS_ICONS, x, y, 16 + (hungry ? 13 * 9 : 0), 27, 9, 9);
            if (i * 2 + 1 < foodLevel) graphics.blit(LADS_ICONS, x, y, (hungry ? 52 : 16) + 36, 27, 9, 9);
            if (i * 2 + 1 == foodLevel) graphics.blit(LADS_ICONS, x, y, (hungry ? 52 : 16) + 45, 27, 9, 9);
        }
    }
}
