package com.thelads.core.v26_2.cosmetics.anim.mixin;

import com.thelads.core.v26_2.cosmetics.anim.EmoteAnim26;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While the local player's emote moves the arms, the normal hand/item lowers as for an item swap; afterwards vanilla raises it again. */
@Mixin(FirstPersonHandsAndItems.class)
public class EmoteHandHeightMixin26 {
    @Shadow private float mainHandHeight, oMainHandHeight, offHandHeight, oOffHandHeight;

    @Inject(method = "tick", at = @At("TAIL"))
    private void lads$emoteLowersHands(LocalPlayer player, CallbackInfo callback) {
        if (!EmoteAnim26.handsDown(player)) return;
        mainHandHeight = Math.max(0, oMainHandHeight - 0.4f);
        offHandHeight = Math.max(0, oOffHandHeight - 0.4f);
    }
}
