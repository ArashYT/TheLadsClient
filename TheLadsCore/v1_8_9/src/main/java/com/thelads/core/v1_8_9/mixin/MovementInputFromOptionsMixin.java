package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.Toggles189;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.MovementInputFromOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Toggle Sprint &amp; Sneak: the movement input's read of the Sneak key sees the toggle. */
@Mixin(MovementInputFromOptions.class)
public abstract class MovementInputFromOptionsMixin {
    @Redirect(method = "updatePlayerMoveState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/settings/KeyBinding;isKeyDown()Z"), require = 1)
    private boolean ladsSneakKey(KeyBinding binding) {
        return Toggles189.isKeyDown(binding);
    }
}
