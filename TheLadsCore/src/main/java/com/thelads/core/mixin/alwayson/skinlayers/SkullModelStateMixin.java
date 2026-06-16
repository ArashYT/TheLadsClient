package com.thelads.core.mixin.alwayson.skinlayers;

import com.thelads.core.features.alwayson.skinlayers.accessor.SkullModelStateAccessor;
import com.thelads.core.features.alwayson.skinlayers.accessor.SkullSettings;
import net.minecraft.client.model.object.skull.SkullModelBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(SkullModelBase.State.class)
public class SkullModelStateMixin implements SkullModelStateAccessor {
    @Unique
    private SkullSettings skullSettings = null;

    @Override
    public SkullSettings getSkullSettings() {
        return this.skullSettings;
    }

    @Override
    public void setSkullSettings(SkullSettings skullSettings) {
        this.skullSettings = skullSettings;
    }
}
