/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.renderer.entity.state.AvatarRenderState
 */
package dev.tr7zw.notenoughanimations.util;

import dev.tr7zw.notenoughanimations.versionless.animations.DataHolder;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

public class RenderStateHolder
implements DataHolder<RenderStateHolder.RenderStateData> {
    public static final RenderStateHolder INSTANCE = new RenderStateHolder();

    public static class RenderStateData {
        public AvatarRenderState renderState;
    }
}

