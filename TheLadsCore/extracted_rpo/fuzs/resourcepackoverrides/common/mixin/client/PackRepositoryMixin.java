/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.collect.Lists
 *  com.llamalad7.mixinextras.injector.ModifyReturnValue
 *  net.minecraft.client.Minecraft
 *  net.minecraft.server.packs.repository.Pack
 *  net.minecraft.server.packs.repository.Pack$Position
 *  net.minecraft.server.packs.repository.PackRepository
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package fuzs.resourcepackoverrides.common.mixin.client;

import com.google.common.collect.Lists;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import fuzs.resourcepackoverrides.common.server.packs.PackSelectionOverride;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={PackRepository.class})
abstract class PackRepositoryMixin {
    @Shadow
    private Map<String, Pack> available;

    PackRepositoryMixin() {
    }

    @Inject(method={"reload"}, at={@At(value="INVOKE", target="Lnet/minecraft/server/packs/repository/PackRepository;rebuildSelected(Ljava/util/Collection;)Ljava/util/List;")})
    public void reload(CallbackInfo callback) {
        if (PackRepository.class.cast(this) != Minecraft.getInstance().getResourcePackRepository()) {
            return;
        }
        this.available.values().forEach(PackSelectionOverride::applyPackOverride);
    }

    @ModifyReturnValue(method={"rebuildSelected"}, at={@At(value="TAIL")})
    private List<Pack> rebuildSelected(List<Pack> packs) {
        int i;
        if (PackRepository.class.cast(this) != Minecraft.getInstance().getResourcePackRepository()) {
            return packs;
        }
        for (i = 0; i < packs.size(); ++i) {
            if (packs.get(i).getDefaultPosition() != Pack.Position.BOTTOM) {
                return packs;
            }
            if (((Pack)packs.get(i)).getId().equals("vanilla")) break;
        }
        if (i != 0) {
            packs = Lists.newArrayList(packs);
            packs.add(0, (Pack)packs.remove(i));
        }
        return packs;
    }
}

