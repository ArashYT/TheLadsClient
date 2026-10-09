package com.thelads.core.v1_20_1.game.skin.mixin;

import com.thelads.core.v1_20_1.game.skin.SkinLayers1201;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Players drawn as a GUI preview (inventory, creative tab, anything using this helper) follow SkinLayers' "3D Preview" option. */
@Mixin(InventoryScreen.class)
abstract class InventoryScreenMixin {
    private static final String RENDER = "renderEntityInInventory(Lnet/minecraft/client/gui/GuiGraphics;IIILorg/joml/Quaternionf;Lorg/joml/Quaternionf;Lnet/minecraft/world/entity/LivingEntity;)V";

    @Inject(method = RENDER, at = @At("HEAD"))
    private static void lads$previewBegin(GuiGraphics graphics, int x, int y, int scale, Quaternionf pose, Quaternionf camera, LivingEntity entity,
                                          CallbackInfo callback) {
        SkinLayers1201.beginGui();
    }

    @Inject(method = RENDER, at = @At("TAIL"))
    private static void lads$previewEnd(GuiGraphics graphics, int x, int y, int scale, Quaternionf pose, Quaternionf camera, LivingEntity entity,
                                        CallbackInfo callback) {
        SkinLayers1201.endGui();
    }
}
