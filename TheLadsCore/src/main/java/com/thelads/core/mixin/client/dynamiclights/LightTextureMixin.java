package com.thelads.core.mixin.client.dynamiclights;

import com.thelads.core.modules.DynamicLightsModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockModelLighter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockModelLighter.class)
public class LightTextureMixin {

    @Inject(method = "getLightCoords", at = @At("RETURN"), cancellable = true)
    private void onGetLightCoords(BlockState state, BlockAndTintGetter level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        DynamicLightsModule module = DynamicLightsModule.getInstance();
        if (module == null || !module.isEnabled()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            Item mainHand = mc.player.getMainHandItem().getItem();
            Item offHand = mc.player.getOffhandItem().getItem();

            boolean holdingLight = (mainHand == Items.TORCH || mainHand == Items.LANTERN || mainHand == Items.SOUL_TORCH || mainHand == Items.SOUL_LANTERN || mainHand == Items.GLOWSTONE || mainHand == Items.SHROOMLIGHT ||
                                    offHand == Items.TORCH || offHand == Items.LANTERN || offHand == Items.SOUL_TORCH || offHand == Items.SOUL_LANTERN || offHand == Items.GLOWSTONE || offHand == Items.SHROOMLIGHT);

            if (holdingLight) {
                double dist = mc.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                if (dist < 256.0) { // Max radius 16 blocks
                    int current = cir.getReturnValue();
                    int blockLight = current & 0xFF;
                    int skyLight = (current >> 16) & 0xFF;
                    
                    int boost = (int)(15 - Math.sqrt(dist));
                    if (boost > blockLight) {
                        blockLight = boost;
                        cir.setReturnValue(blockLight | (skyLight << 16));
                    }
                }
            }
        }
    }
}
