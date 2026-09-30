package com.thelads.core.v26_2.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(net.minecraft.client.player.FirstPersonHandsAndItems.class)
public class LegacyEquipMixin {
    @org.spongepowered.asm.mixin.Shadow private float mainHandHeight, oMainHandHeight, offHandHeight, oOffHandHeight;
    @org.spongepowered.asm.mixin.Shadow private net.minecraft.world.item.ItemStack mainHandItem, offHandItem;
    @Inject(method={"tick","itemUsed"}, at=@At("TAIL"), require=1)
    private void ladsNoReequip(CallbackInfo ci) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null || !com.thelads.core.v26_2.feature.NativeQualityOfLife.enabled("LegacySwing")) return;
        mainHandItem = player.getMainHandItem(); offHandItem = player.getOffhandItem();
        mainHandHeight = oMainHandHeight = offHandHeight = oOffHandHeight = 1;
    }

}
