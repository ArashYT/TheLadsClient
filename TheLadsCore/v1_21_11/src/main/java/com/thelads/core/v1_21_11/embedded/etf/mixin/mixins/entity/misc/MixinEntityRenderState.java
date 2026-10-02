package com.thelads.core.v1_21_11.embedded.etf.mixin.mixins.entity.misc;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import com.thelads.core.v1_21_11.embedded.etf.ETF;
import com.thelads.core.v1_21_11.embedded.etf.features.state.ETFEntityRenderState;
import com.thelads.core.v1_21_11.embedded.etf.features.state.HoldsETFRenderState;
import com.thelads.core.v1_21_11.embedded.etf.utils.ETFEntity;

@Mixin(EntityRenderState.class)
public class MixinEntityRenderState implements HoldsETFRenderState {

    @Unique
    private ETFEntityRenderState etf$state = null;

    @Override
    public @NotNull ETFEntityRenderState etf$getState() {
        assert this.etf$state != null; // something has gone badly wrong usage wise otherwise
        return etf$state;
    }

    @Override
    public void etf$initState(@NotNull final ETFEntity entity) {
        etf$state = ETFEntityRenderState.forEntity(entity);
        etf$state.setVanillaState((EntityRenderState) (Object) this);
    }
}
