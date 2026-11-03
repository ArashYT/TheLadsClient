package com.thelads.core.v26_2.mixin;

import com.thelads.core.client.ViaForce;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** ViaFabricPlus' forced version under a tag every Lads version reads, as servers.dat is shared by them (ViaForce.TAG). */
@Mixin(ServerData.class)
abstract class ServerDataForceMixin {
    @Inject(method = "write", at = @At("RETURN"))
    private void ladsWriteForced(CallbackInfoReturnable<CompoundTag> cir) {
        Integer forced = ViaForce.forced(this);
        if (forced != null) cir.getReturnValue().putInt(ViaForce.TAG, forced);
    }

    @Inject(method = "read", at = @At("RETURN"))
    private static void ladsReadForced(CompoundTag tag, CallbackInfoReturnable<ServerData> cir) {
        tag.getInt(ViaForce.TAG).ifPresent(forced -> ViaForce.force(cir.getReturnValue(), forced));
    }
}
