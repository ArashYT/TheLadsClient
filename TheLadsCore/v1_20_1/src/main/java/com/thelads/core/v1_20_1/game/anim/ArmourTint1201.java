package com.thelads.core.v1_20_1.game.anim;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/**
 * 1.7 Animations' Red armour on hurt: armour is drawn without the hurt overlay, so a hurt body's red reaches it as a vertex colour.
 * This buffer source stands in for the real one while an armour layer draws and reddens the armour pieces' vertices only.
 */
public final class ArmourTint1201 implements MultiBufferSource {
    private final MultiBufferSource delegate;

    public ArmourTint1201(MultiBufferSource delegate) {
        this.delegate = delegate;
    }

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        VertexConsumer buffer = delegate.getBuffer(type);
        float tint = NativeOldAnimations1201.armourGreenBlue();
        // RenderType.toString() is "RenderType[armor_cutout_no_cull:...]": the armour pieces; trims and glint keep their colours.
        return tint < 1 && type.toString().contains("armor_cutout_no_cull") ? new Tinted(buffer, tint) : buffer;
    }

    private record Tinted(VertexConsumer delegate, float tint) implements VertexConsumer {
        @Override public VertexConsumer vertex(double x, double y, double z) { delegate.vertex(x, y, z); return this; }
        @Override public VertexConsumer color(int r, int g, int b, int a) { delegate.color(r, Math.round(g * tint), Math.round(b * tint), a); return this; }
        @Override public VertexConsumer uv(float u, float v) { delegate.uv(u, v); return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { delegate.overlayCoords(u, v); return this; }
        @Override public VertexConsumer uv2(int u, int v) { delegate.uv2(u, v); return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { delegate.normal(x, y, z); return this; }
        @Override public void endVertex() { delegate.endVertex(); }
        @Override public void defaultColor(int r, int g, int b, int a) { delegate.defaultColor(r, g, b, a); }
        @Override public void unsetDefaultColor() { delegate.unsetDefaultColor(); }
    }
}
