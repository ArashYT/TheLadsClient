package com.thelads.core.v1_20_1.mixin.menus;

import com.thelads.core.client.ViaForce;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ViaFabricPlus' forced version under a tag every Lads version reads, as servers.dat is shared by them (ViaForce.TAG). The newer
 * ViaFabricPlus' tag is a version name, which 2.8.7 here reads as protocol 0 and would force the server to "unknown": it never sees that one.
 */
@Mixin(ServerData.class)
abstract class ServerDataForceMixin {
    @Inject(method = "write", at = @At("RETURN"))
    private void ladsWriteForced(CallbackInfoReturnable<CompoundTag> cir) {
        Integer forced = ViaForce.forced(this);
        if (forced != null) cir.getReturnValue().putInt(ViaForce.TAG, forced);
    }

    @Inject(method = "read", at = @At("HEAD"))
    private static void ladsDropNewerTag(CompoundTag tag, CallbackInfoReturnable<ServerData> cir) {
        if (tag.contains(ViaForce.VIA_TAG, Tag.TAG_STRING)) tag.remove(ViaForce.VIA_TAG);
    }

    @Inject(method = "read", at = @At("RETURN"))
    private static void ladsReadForced(CompoundTag tag, CallbackInfoReturnable<ServerData> cir) {
        if (tag.contains(ViaForce.TAG, Tag.TAG_INT)) ViaForce.force(cir.getReturnValue(), tag.getInt(ViaForce.TAG));
    }
}
