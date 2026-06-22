/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.vertex.PoseStack
 */
package dev.ultimatchamp.enhancedtooltips.util;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix3x2fStack;

public record MatricesUtil(Object matrixStack) {
    public void pushMatrix() {
        Object object = this.matrixStack;
        if (object instanceof Matrix3x2fStack) {
            Matrix3x2fStack m = (Matrix3x2fStack)((Object)object);
            m.pushMatrix();
        } else {
            object = this.matrixStack;
            if (object instanceof PoseStack) {
                PoseStack m = (PoseStack)object;
                m.pushPose();
            }
        }
    }

    public void popMatrix() {
        Object object = this.matrixStack;
        if (object instanceof Matrix3x2fStack) {
            Matrix3x2fStack m = (Matrix3x2fStack)((Object)object);
            m.popMatrix();
        } else {
            object = this.matrixStack;
            if (object instanceof PoseStack) {
                PoseStack m = (PoseStack)object;
                m.popPose();
            }
        }
    }

    public void trans(float x, float y, float z) {
        Object object = this.matrixStack;
        if (object instanceof Matrix3x2fStack) {
            Matrix3x2fStack m = (Matrix3x2fStack)((Object)object);
            m.translate(x, y);
        } else {
            object = this.matrixStack;
            if (object instanceof PoseStack) {
                PoseStack m = (PoseStack)object;
                m.translate(x, y, z);
            }
        }
    }

    public void scal(float x, float y, float z) {
        Object object = this.matrixStack;
        if (object instanceof Matrix3x2fStack) {
            Matrix3x2fStack m = (Matrix3x2fStack)((Object)object);
            m.scale(x, y);
        } else {
            object = this.matrixStack;
            if (object instanceof PoseStack) {
                PoseStack m = (PoseStack)object;
                m.scale(x, y, z);
            }
        }
    }
}

