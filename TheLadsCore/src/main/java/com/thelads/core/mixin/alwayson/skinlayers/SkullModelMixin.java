package com.thelads.core.mixin.alwayson.skinlayers;

import com.thelads.core.features.alwayson.skinlayers.accessor.ModelPartInjector;
import com.thelads.core.features.alwayson.skinlayers.accessor.SkullModelAccessor;
import com.thelads.core.features.alwayson.skinlayers.accessor.SkullModelStateAccessor;
import com.thelads.core.features.alwayson.skinlayers.api.Mesh;
import com.thelads.core.features.alwayson.skinlayers.api.OffsetProvider;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.object.skull.SkullModel;
import net.minecraft.client.model.object.skull.SkullModelBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkullModel.class)
public class SkullModelMixin implements SkullModelAccessor {
    @Shadow
    private ModelPart head;

    @Override
    public void injectHatMesh(Mesh mesh) {
        this.head.getAllParts().forEach(part -> {
            if (part != this.head && (Object) part instanceof ModelPartInjector) {
                ModelPartInjector inj = (ModelPartInjector) (Object) part;
                inj.setInjectedMesh(mesh, OffsetProvider.SKULL);
            }
        });
    }

    @Inject(method = "setupAnim", at = @At("HEAD"))
    public void setupAnim(SkullModelBase.State state, CallbackInfo ci) {
        if (state instanceof SkullModelStateAccessor && com.thelads.core.config.ModuleManager.getInstance().getModule("SkinLayers").isEnabled()) {
            SkullModelStateAccessor accessor = (SkullModelStateAccessor) state;
            if (accessor.getSkullSettings() != null) {
                this.injectHatMesh(accessor.getSkullSettings().getMesh());
                return;
            }
        }
        this.injectHatMesh(null);
    }
}
