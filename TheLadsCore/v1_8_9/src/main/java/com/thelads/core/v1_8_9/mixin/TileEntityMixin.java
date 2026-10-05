package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.Options189;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * FarBlockEntities, as 26.x FarBlockEntitiesMixin: only the inherited render distance grows (64 blocks in vanilla) to the
 * module's Distance; renderers with a longer one (beacons) keep it. Loaded chunks and render distance still limit what is drawn.
 * Entity Culling: the culling thread's verdict on each block entity, and when it was last drawn (EntityCulling189.Cullable).
 */
@Mixin(TileEntity.class)
public abstract class TileEntityMixin implements com.thelads.core.v1_8_9.feature.EntityCulling189.Cullable {
    private int ladsCullGen, ladsSeen;

    @Override public int ladsCullGen() { return ladsCullGen; }
    @Override public void ladsCullGen(int gen) { ladsCullGen = gen; }
    @Override public int ladsSeen() { return ladsSeen; }
    @Override public void ladsSeen(int frame) { ladsSeen = frame; }

    // HEAD, not RETURN: Mixin 0.7 injects a bad dup at a double return (VerifyError). The base method always returns 64 blocks.
    @Inject(method = "getMaxRenderDistanceSquared", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsFarBlockEntities(CallbackInfoReturnable<Double> cir) {
        if (!Options189.enabled("FarBlockEntities")) return;
        double distance = Math.max(64, Math.min(256, Options189.number("FarBlockEntities", "Distance", 128)));
        cir.setReturnValue(distance * distance);
    }
}
