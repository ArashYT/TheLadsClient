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

            boolean underwaterEnabled = true;
            com.thelads.core.config.Option underOpt = module.getOption("Underwater");
            if (underOpt instanceof com.thelads.core.config.BoolOption bo) {
                underwaterEnabled = bo.get();
            }
            if (!underwaterEnabled && mc.player.isInWater()) {
                holdingLight = false;
            }

            boolean entitiesEnabled = true;
            com.thelads.core.config.Option entOpt = module.getOption("Entities");
            if (entOpt instanceof com.thelads.core.config.BoolOption bo) {
                entitiesEnabled = bo.get();
            }

            boolean droppedItemsEnabled = true;
            com.thelads.core.config.Option dropOpt = module.getOption("Dropped Items");
            if (dropOpt instanceof com.thelads.core.config.BoolOption bo) {
                droppedItemsEnabled = bo.get();
            }

            com.thelads.core.config.Option qualityOpt = module.getOption("Quality");
            boolean isFancy = true;
            if (qualityOpt instanceof com.thelads.core.config.DropdownOption dropdown) {
                isFancy = dropdown.getIndex() == 1;
            }
            if (!isFancy) {
                entitiesEnabled = false;
                droppedItemsEnabled = false;
            }

            boolean lightSourcePresent = holdingLight;
            double minDist = Double.MAX_VALUE;
            double targetX = pos.getX() + 0.5;
            double targetY = pos.getY() + 0.5;
            double targetZ = pos.getZ() + 0.5;

            if (holdingLight) {
                minDist = mc.player.distanceToSqr(targetX, targetY, targetZ);
            }

            if (entitiesEnabled && mc.level != null) {
                for (net.minecraft.world.entity.Entity entity : mc.level.entitiesForRendering()) {
                    if (entity != mc.player && entity instanceof net.minecraft.world.entity.LivingEntity living) {
                        Item main = living.getMainHandItem().getItem();
                        Item off = living.getOffhandItem().getItem();
                        boolean isHolding = (main == Items.TORCH || main == Items.LANTERN || main == Items.SOUL_TORCH || main == Items.SOUL_LANTERN || main == Items.GLOWSTONE || main == Items.SHROOMLIGHT ||
                                             off == Items.TORCH || off == Items.LANTERN || off == Items.SOUL_TORCH || off == Items.SOUL_LANTERN || off == Items.GLOWSTONE || off == Items.SHROOMLIGHT);
                        if (isHolding) {
                            double d = entity.distanceToSqr(targetX, targetY, targetZ);
                            if (d < minDist) {
                                minDist = d;
                                lightSourcePresent = true;
                            }
                        }
                    }
                }
            }

            if (droppedItemsEnabled && mc.level != null) {
                for (net.minecraft.world.entity.Entity entity : mc.level.entitiesForRendering()) {
                    if (entity instanceof net.minecraft.world.entity.item.ItemEntity itemEntity) {
                        Item item = itemEntity.getItem().getItem();
                        boolean isLight = (item == Items.TORCH || item == Items.LANTERN || item == Items.SOUL_TORCH || item == Items.SOUL_LANTERN || item == Items.GLOWSTONE || item == Items.SHROOMLIGHT);
                        if (isLight) {
                            double d = entity.distanceToSqr(targetX, targetY, targetZ);
                            if (d < minDist) {
                                minDist = d;
                                lightSourcePresent = true;
                            }
                        }
                    }
                }
            }

            if (lightSourcePresent) {
                com.thelads.core.config.Option opt = module.getOption("Light Radius");
                double radius = 15.0;
                if (opt instanceof com.thelads.core.config.SliderOption) {
                    radius = ((com.thelads.core.config.SliderOption) opt).getValue();
                }

                if (minDist < (radius * radius)) {
                    int current = cir.getReturnValue();
                    int blockLight = current & 0xFF;
                    int skyLight = (current >> 16) & 0xFF;

                    int boost = (int)(radius - Math.sqrt(minDist));
                    if (boost > blockLight) {
                        blockLight = boost;
                        cir.setReturnValue(blockLight | (skyLight << 16));
                    }
                }
            }
        }
    }
}
