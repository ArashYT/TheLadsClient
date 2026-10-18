package com.thelads.smp.features.arise.nb10.client;

import com.thelads.smp.features.arise.nb10.DecorationType;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

import java.util.ArrayList;
import java.util.List;

/**
 * Render state container for FastenerBlockEntity in Minecraft 26.3.
 */
public class FastenerRenderState extends BlockEntityRenderState {

    public final List<CachedConnectionMesh> meshes = new ArrayList<>();
    public final List<DecorationType> types = new ArrayList<>();
    public float windTime = 0.0F;
    public boolean windSwayEnabled = true;
    public boolean culled = false;

    public void clear() {
        meshes.clear();
        types.clear();
        culled = false;
    }
}
