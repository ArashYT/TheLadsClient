/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Options
 *  net.minecraft.server.packs.repository.Pack
 *  net.minecraft.server.packs.repository.PackRepository
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.At$Shift
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package fuzs.resourcepackoverrides.common.mixin.client;

import fuzs.resourcepackoverrides.common.config.ResourceOverridesManager;
import java.util.List;
import net.minecraft.client.Options;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={Options.class}, priority=2000)
abstract class OptionsMixin {
    @Shadow
    public List<String> resourcePacks;
    @Shadow
    public List<String> incompatibleResourcePacks;
    @Unique
    private boolean resourcePackOverrides$wasEmpty;

    OptionsMixin() {
    }

    @Inject(method={"load"}, at={@At(value="HEAD")})
    private void load$0(CallbackInfo callback) {
        this.resourcePackOverrides$wasEmpty = this.resourcePacks.isEmpty();
    }

    @Inject(method={"load"}, at={@At(value="INVOKE", target="Lnet/minecraft/client/KeyMapping;resetMapping()V", shift=At.Shift.AFTER)})
    private void load$1(CallbackInfo callback) {
        this.resourcePackOverrides$wasEmpty = this.resourcePacks.isEmpty();
    }

    @Inject(method={"load"}, at={@At(value="RETURN")})
    private void load$2(CallbackInfo callback) {
        if (this.resourcePackOverrides$wasEmpty) {
            List<String> defaultResourcePacks = ResourceOverridesManager.getDefaultResourcePacks(false);
            this.resourcePacks.removeAll(defaultResourcePacks);
            this.resourcePacks.addAll(defaultResourcePacks);
        }
    }

    @Inject(method={"loadSelectedResourcePacks"}, at={@At(value="HEAD")})
    public void loadSelectedResourcePacks(PackRepository resourcePackList, CallbackInfo callback) {
        if (this.resourcePackOverrides$wasEmpty) {
            this.resourcePackOverrides$wasEmpty = false;
            for (String packName : ResourceOverridesManager.getDefaultResourcePacks(false)) {
                Pack pack = resourcePackList.getPack(packName);
                if (pack == null && !packName.startsWith("file/")) {
                    pack = resourcePackList.getPack("file/" + packName);
                }
                if (pack == null || pack.getCompatibility().isCompatible()) continue;
                this.incompatibleResourcePacks.add(packName);
            }
        }
    }
}

