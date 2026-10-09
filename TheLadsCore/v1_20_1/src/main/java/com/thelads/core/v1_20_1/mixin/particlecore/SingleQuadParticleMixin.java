package com.thelads.core.v1_20_1.mixin.particlecore;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thelads.core.client.ParticleCore;
import com.thelads.core.v1_20_1.feature.ParticleCore1201;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The drawing side of Particle Core for the flat particles that make almost all of them. Culling: a quad wholly outside the frame's
 * frustum is not built. Optimized rendering: the four corners come straight from the camera's own left and up vectors (plus the
 * roll), with no rotation per corner and nothing allocated; the quad is the one vanilla builds. Sodium and Embeddium draw these
 * quads their own way, so with either installed the vertex work is left to them. The late priority puts this after their rewrite
 * of the method, so the culling test still comes first.
 */
@Mixin(value = SingleQuadParticle.class, priority = 1500)
public abstract class SingleQuadParticleMixin extends Particle {
    protected SingleQuadParticleMixin(ClientLevel level, double x, double y, double z) { super(level, x, y, z); }

    @Shadow public abstract float getQuadSize(float partialTick);
    @Shadow protected abstract float getU0();
    @Shadow protected abstract float getU1();
    @Shadow protected abstract float getV0();
    @Shadow protected abstract float getV1();

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void ladsRender(VertexConsumer buffer, Camera camera, float partialTick, CallbackInfo ci) {
        boolean cull = ParticleCore.culling() && camera == ParticleCore1201.frameCamera;
        boolean fast = ParticleCore.optimized() && !ParticleCore1201.deferVertices();
        if (!cull && !fast) return;
        Vec3 view = camera.getPosition();
        float px = (float) (Mth.lerp((double) partialTick, this.xo, this.x) - view.x());
        float py = (float) (Mth.lerp((double) partialTick, this.yo, this.y) - view.y());
        float pz = (float) (Mth.lerp((double) partialTick, this.zo, this.z) - view.z());
        float size = this.getQuadSize(partialTick);
        if (cull && !ParticleCore.FRUSTUM.visible(px, py, pz, Math.abs(size) * 1.5 + 0.05)) { ParticleCore.culled++; ci.cancel(); return; }
        ParticleCore.drawn++;
        if (!fast) return;
        Vector3f left = camera.getLeftVector(), up = camera.getUpVector();
        float rx = left.x(), ry = left.y(), rz = left.z(), ux = up.x(), uy = up.y(), uz = up.z();
        if (this.roll != 0.0F) { // vanilla turns the quad about the camera's forward axis by the interpolated roll
            float angle = Mth.lerp(partialTick, this.oRoll, this.roll), cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
            float nrx = rx * cos + ux * sin, nry = ry * cos + uy * sin, nrz = rz * cos + uz * sin;
            ux = ux * cos - rx * sin; uy = uy * cos - ry * sin; uz = uz * cos - rz * sin;
            rx = nrx; ry = nry; rz = nrz;
        }
        rx *= size; ry *= size; rz *= size; ux *= size; uy *= size; uz *= size;
        float u0 = this.getU0(), u1 = this.getU1(), v0 = this.getV0(), v1 = this.getV1();
        int light = this.getLightColor(partialTick);
        buffer.vertex(px - rx - ux, py - ry - uy, pz - rz - uz).uv(u1, v1).color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
        buffer.vertex(px - rx + ux, py - ry + uy, pz - rz + uz).uv(u1, v0).color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
        buffer.vertex(px + rx + ux, py + ry + uy, pz + rz + uz).uv(u0, v0).color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
        buffer.vertex(px + rx - ux, py + ry - uy, pz + rz - uz).uv(u0, v1).color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
        ci.cancel();
    }
}
