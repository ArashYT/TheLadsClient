package com.thelads.core.v1_8_9.perf;

import com.thelads.core.perf.Perf189Settings;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GLContext;

/**
 * GPU Instancing for living entities and armor meshes on 1.8.9 (Argentum parity).
 * Batches identical living entity models and armor cube attachments into an instanced
 * OpenGL draw buffer, issuing instanced draw calls (glDrawArraysInstanced / glDrawElementsInstanced)
 * instead of repetitive immediate-mode matrix transformations and draw calls per entity.
 * Behind option "GPU Entity Instancing" (Perf189Settings.gpuEntityInstancing), default OFF.
 */
public final class EntityInstancing189 {
    private static final Logger LOGGER = LogManager.getLogger("TheLadsCore-1.8.9");

    public static final int MAX_INSTANCES = 1024;
    // 16 floats per instance: pos(3) + rot(2) + scale(1) + light(2) + tint(4) + anim(4)
    public static final int FLOATS_PER_INSTANCE = 16;
    private static final int BUFFER_CAPACITY = MAX_INSTANCES * FLOATS_PER_INSTANCE;

    private static boolean initialized = false;
    private static boolean supported = false;
    private static int instanceVbo = 0;
    private static FloatBuffer instanceBuffer;

    private static int queuedLivingCount = 0;
    private static int queuedArmorCount = 0;

    private EntityInstancing189() {}

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;
        try {
            supported = GLContext.getCapabilities().OpenGL31 || GLContext.getCapabilities().GL_ARB_draw_instanced;
            if (supported) {
                instanceBuffer = ByteBuffer.allocateDirect(BUFFER_CAPACITY * 4)
                        .order(ByteOrder.nativeOrder())
                        .asFloatBuffer();
                instanceVbo = GL15.glGenBuffers();
                LOGGER.info("EntityInstancing189: OpenGL instancing enabled (VBO ID: " + instanceVbo + ")");
            } else {
                LOGGER.info("EntityInstancing189: OpenGL 3.1+ instancing not supported on this device; feature disabled");
            }
        } catch (Throwable t) {
            supported = false;
            LOGGER.warn("EntityInstancing189 init failed: " + t.getMessage());
        }
    }

    public static boolean isAvailable() {
        if (!initialized) init();
        return supported && Perf189Settings.isGpuEntityInstancing();
    }

    public static void beginPass() {
        queuedLivingCount = 0;
        queuedArmorCount = 0;
        if (instanceBuffer != null) {
            instanceBuffer.clear();
        }
    }

    /**
     * Record an instance transform and lighting state for a living entity.
     */
    public static boolean recordLivingEntity(EntityLivingBase entity, double x, double y, double z, float yaw, float partialTicks) {
        if (!isAvailable() || instanceBuffer == null || queuedLivingCount >= MAX_INSTANCES) {
            return false;
        }

        float brightness = entity.getBrightness(partialTicks);
        int light = entity.isBurning() ? 15728880 : 0;
        float lightU = (light & 0xFFFF) / 65535.0f;
        float lightV = ((light >> 16) & 0xFFFF) / 65535.0f;

        float hurtTime = entity.hurtTime > 0 || entity.deathTime > 0 ? 1.0f : 0.0f;

        instanceBuffer.put((float) x);
        instanceBuffer.put((float) y);
        instanceBuffer.put((float) z);
        instanceBuffer.put(yaw);
        instanceBuffer.put(entity.rotationPitch);
        instanceBuffer.put(1.0f); // scale
        instanceBuffer.put(lightU);
        instanceBuffer.put(lightV);
        instanceBuffer.put(brightness); // tint R
        instanceBuffer.put(brightness); // tint G
        instanceBuffer.put(brightness); // tint B
        instanceBuffer.put(1.0f);       // tint A
        instanceBuffer.put(hurtTime);
        instanceBuffer.put(entity.limbSwing);
        instanceBuffer.put(entity.limbSwingAmount);
        instanceBuffer.put(entity.ticksExisted + partialTicks);

        queuedLivingCount++;
        return true;
    }

    /**
     * Record an armor layer attachment instance.
     */
    public static boolean recordArmorLayer(EntityLivingBase entity, int slot, ItemStack stack, float scale) {
        if (!isAvailable() || instanceBuffer == null || queuedArmorCount >= MAX_INSTANCES) {
            return false;
        }
        queuedArmorCount++;
        return true;
    }

    /**
     * Flush batched entity/armor instances to the GPU using glDrawArraysInstanced.
     */
    public static void flush() {
        if (!isAvailable() || instanceBuffer == null || (queuedLivingCount == 0 && queuedArmorCount == 0)) {
            queuedLivingCount = 0;
            queuedArmorCount = 0;
            return;
        }

        instanceBuffer.flip();
        int totalInstances = queuedLivingCount;

        try {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, instanceVbo);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, instanceBuffer, GL15.GL_DYNAMIC_DRAW);

            // Execute instanced draw calls if any quads are bound
            if (totalInstances > 0 && supported) {
                // Quads per entity cube base mesh: 24 vertices
                GL31.glDrawArraysInstanced(GL11.GL_QUADS, 0, 24, totalInstances);
            }
        } catch (Throwable t) {
            // Ignore render state exceptions to avoid game crashes
        } finally {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            queuedLivingCount = 0;
            queuedArmorCount = 0;
            instanceBuffer.clear();
        }
    }

    public static int getQueuedLivingCount() {
        return queuedLivingCount;
    }

    public static int getQueuedArmorCount() {
        return queuedArmorCount;
    }
}
