package com.thelads.core.v1_21_11.mixin;

import com.thelads.core.client.ViaForce;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** ViaFabricPlus' forced version under a tag every Lads version reads, as servers.dat is shared by them (ViaForce.TAG). */
@Mixin(ServerData.class)
abstract class ServerDataForceMixin {
    /** A forced protocol this ViaFabricPlus cannot apply: kept for the versions that can. */
    @Unique private Integer ladsUnapplied;

    @Inject(method = "write", at = @At("RETURN"))
    private void ladsWriteForced(CallbackInfoReturnable<CompoundTag> cir) {
        Integer forced = ViaForce.forced(this);
        if (forced == null) forced = ladsUnapplied;
        if (forced != null) cir.getReturnValue().putInt(ViaForce.TAG, forced);
    }

    @Inject(method = "read", at = @At("RETURN"))
    private static void ladsReadForced(CompoundTag tag, CallbackInfoReturnable<ServerData> cir) {
        if (tag.getString(ViaForce.VIA_TAG).isPresent()) return; // ViaFabricPlus' own name is exact; a bare number is ambiguous for its legacy versions
        java.util.Optional<Integer> forced = tag.getInt(ViaForce.TAG);
        if (forced.isPresent() && !ViaForce.force(cir.getReturnValue(), forced.get())) ((ServerDataForceMixin) (Object) cir.getReturnValue()).ladsUnapplied = forced.get();
    }
}
