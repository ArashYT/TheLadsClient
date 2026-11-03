package com.thelads.core.v1_8_9.mixin;

import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.nbt.NBTTagCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * servers.dat is shared by every Lads version and 1.8.9's ServerData only knows name, ip, icon and acceptTextures, so a save here would
 * drop the rest (a server's forced version, the hidden flag). Each entry keeps the other tags it came with and writes them back.
 */
@Mixin(ServerData.class)
abstract class ServerDataKeepTagsMixin189 {
    @Unique private NBTTagCompound ladsOtherTags;

    @Inject(method = "getServerDataFromNBTCompound", at = @At("RETURN"))
    private static void ladsKeepTags(NBTTagCompound tag, CallbackInfoReturnable<ServerData> cir) {
        NBTTagCompound others = (NBTTagCompound) tag.copy();
        others.removeTag("name");
        others.removeTag("ip");
        others.removeTag("icon");
        others.removeTag("acceptTextures");
        ((ServerDataKeepTagsMixin189) (Object) cir.getReturnValue()).ladsOtherTags = others;
    }

    @Inject(method = "getNBTCompound", at = @At("RETURN"))
    private void ladsWriteTags(CallbackInfoReturnable<NBTTagCompound> cir) {
        if (ladsOtherTags != null) cir.getReturnValue().merge(ladsOtherTags);
    }
}
