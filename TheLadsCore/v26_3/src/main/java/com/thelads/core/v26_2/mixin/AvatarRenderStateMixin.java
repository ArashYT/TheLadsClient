package com.thelads.core.v26_2.mixin;

import com.thelads.core.client.ladsapi.LadsBadge;
import com.thelads.core.v26_2.feature.LadsAvatarRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.UUID;

@Mixin(AvatarRenderState.class)
public class AvatarRenderStateMixin implements LadsAvatarRenderState {
    @Unique
    private UUID lads$uuid;
    @Unique
    private boolean lads$hasBadge;

    @Override
    public UUID lads$getUuid() {
        return lads$uuid;
    }

    @Override
    public void lads$setUuid(UUID uuid) {
        this.lads$uuid = uuid;
        this.lads$hasBadge = uuid != null && LadsBadge.showOnNametag(uuid);
    }

    @Override
    public boolean lads$hasBadge() {
        return lads$hasBadge;
    }
}
