package com.thelads.core.mixin.alwayson.skinlayers;

import com.thelads.core.features.alwayson.skinlayers.accessor.AvatarRenderStateAccessor;
import java.lang.ref.WeakReference;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AvatarRenderState.class)
public class AvatarRenderStateMixin implements AvatarRenderStateAccessor {
    @Unique
    private WeakReference<AbstractClientPlayer> skinlayers$playerRef;

    @Override
    public AbstractClientPlayer skinlayers$getPlayer() {
        return this.skinlayers$playerRef != null ? this.skinlayers$playerRef.get() : null;
    }

    @Override
    public void skinlayers$setPlayer(AbstractClientPlayer player) {
        this.skinlayers$playerRef = player != null ? new WeakReference<>(player) : null;
    }
}
