/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.phys.Vec3
 */
package com.thelads.core.features.alwayson.vmp.common.playerwatching.compat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public abstract class EntityPositionTransformer {
    protected abstract Vec3 transform0(Entity var1, Vec3 var2);

    public final Vec3 transform(Entity entity, Vec3 pos) {
        Vec3 pos1;
        try {
            pos1 = this.transform0(entity, pos);
        }
        catch (Throwable t) {
            System.err.println("EntityPositionTransformer %s threw an exception for %s at %s".formatted(this.getClass().getName(), entity, pos));
            t.printStackTrace();
            return pos;
        }
        if (pos1 == null) {
            System.err.println("EntityPositionTransformer %s returned null for %s at %s".formatted(this.getClass().getName(), entity, pos));
            return pos;
        }
        return pos1;
    }
}

