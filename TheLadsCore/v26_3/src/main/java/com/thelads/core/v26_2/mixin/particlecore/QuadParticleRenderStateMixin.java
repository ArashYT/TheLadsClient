package com.thelads.core.v26_2.mixin.particlecore;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thelads.core.client.ParticleCore;
import com.thelads.core.v26_2.feature.NativeParticleCore;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Optimized rendering, vertex side: vanilla builds each flat particle's four corners with a new Quaternionf and four new Vector3f
 * rotated one by one. The rotation turns the quad's two unit axes into the vectors below once, and the corners follow from adding
 * them; the same four vertices come out (to float rounding), with nothing allocated. Left to Sodium where it is installed.
 */
@Mixin(QuadParticleRenderState.class)
public abstract class QuadParticleRenderStateMixin {
    @Inject(method = "renderRotatedQuad", at = @At("HEAD"), cancellable = true)
    private void ladsQuad(VertexConsumer builder, float x, float y, float z, float qx, float qy, float qz, float qw, float scale,
                          float u0, float u1, float v0, float v1, int color, int light, CallbackInfo ci) {
        if (!ParticleCore.optimized() || NativeParticleCore.deferVertices()) return;
        float xx = qx * qx, yy = qy * qy, zz = qz * qz, ww = qw * qw;
        float k = scale / (xx + yy + zz + ww); // the scale and JOML's division by the quaternion's squared length, in one factor
        // The rotated unit X and unit Y axes: the first two columns of the rotation matrix, as JOML's Quaternionf#transform computes them.
        float ax = (ww + xx - yy - zz) * k, ay = 2.0F * (qx * qy + qz * qw) * k, az = 2.0F * (qx * qz - qy * qw) * k;
        float bx = 2.0F * (qx * qy - qz * qw) * k, by = (ww - xx + yy - zz) * k, bz = 2.0F * (qy * qz + qx * qw) * k;
        builder.addVertex(x + ax - bx, y + ay - by, z + az - bz).setUv(u1, v1).setColor(color).setLight(light);
        builder.addVertex(x + ax + bx, y + ay + by, z + az + bz).setUv(u1, v0).setColor(color).setLight(light);
        builder.addVertex(x - ax + bx, y - ay + by, z - az + bz).setUv(u0, v0).setColor(color).setLight(light);
        builder.addVertex(x - ax - bx, y - ay - by, z - az - bz).setUv(u0, v1).setColor(color).setLight(light);
        ci.cancel();
    }

    /** QA timing of the vertex building (a no-op unless ParticleCore.timing). */
    @Inject(method = "buildLayer", at = @At("HEAD"))
    private void ladsBuildBegin(SingleQuadParticle.Layer layer, VertexConsumer builder, CallbackInfo ci) { ParticleCore.timeBegin(); }

    @Inject(method = "buildLayer", at = @At("RETURN"))
    private void ladsBuildEnd(SingleQuadParticle.Layer layer, VertexConsumer builder, CallbackInfo ci) { ParticleCore.timeEnd(); }
}
