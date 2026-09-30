package com.thelads.core.v26_2.mixin;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(ModelPart.class)
public interface ModelPartAccessor {
    @Accessor("cubes") java.util.List<ModelPart.Cube> ladsCubes();
    @Mutable @Accessor("cubes") void ladsCubes(java.util.List<ModelPart.Cube> cubes);
}
