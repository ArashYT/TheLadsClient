/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.server.packs.repository.PackRepository
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.At$Shift
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package fuzs.resourcepackoverrides.common.mixin.client;

import fuzs.resourcepackoverrides.common.config.ResourceOverridesManager;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={Minecraft.class})
abstract class MinecraftMixin {
    @Shadow
    @Final
    private PackRepository resourcePackRepository;

    MinecraftMixin() {
    }

    @Inject(method={"clearResourcePacksOnError"}, at={@At(value="INVOKE", target="Lnet/minecraft/server/packs/repository/PackRepository;setSelected(Ljava/util/Collection;)V", shift=At.Shift.AFTER)})
    public void clearResourcePacksOnError(CallbackInfo callback) {
        this.resourcePackRepository.setSelected(ResourceOverridesManager.getDefaultResourcePacks(true));
    }
}

