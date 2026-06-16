/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.SectionPos
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.phys.Vec3
 *  org.joml.Matrix4dc
 *  org.joml.Vector3d
 */
package com.thelads.core.features.alwayson.vmp.common.playerwatching.compat;

import com.thelads.core.features.alwayson.vmp.common.playerwatching.compat.EntityPositionTransformer;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4dc;
import org.joml.Vector3d;

public class ValkyrienSkies2ShipPositionTransformer
extends EntityPositionTransformer {
    private static final MethodHandle methodVSGameUtilsKt$getShipManagingPos;
    private static final MethodHandle methodShip$getShipToWorld;

    @Override
    public Vec3 transform0(Entity entity, Vec3 pos) {
        try {
            Object ship = methodVSGameUtilsKt$getShipManagingPos.invoke(entity.level(), SectionPos.posToSectionCoord((double)pos.x), SectionPos.posToSectionCoord((double)pos.z));
            if (ship != null) {
                Matrix4dc shipToWorld = (Matrix4dc) methodShip$getShipToWorld.invoke(ship);
                Vector3d transformedPosition = shipToWorld.transformPosition(new Vector3d(pos.x, pos.y, pos.z));
                return new Vec3(transformedPosition.x, transformedPosition.y, transformedPosition.z);
            }
        }
        catch (Throwable t) {
            throw new RuntimeException(t);
        }
        return pos;
    }

    static {
        try {
            Class<?> clazzVSGameUtilsKt = Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt");
            Class<?> clazzShip = Class.forName("org.valkyrienskies.core.api.ships.Ship");
            methodVSGameUtilsKt$getShipManagingPos = MethodHandles.lookup().findStatic(clazzVSGameUtilsKt, "getShipManagingPos", MethodType.methodType(clazzShip, Level.class, Integer.TYPE, Integer.TYPE));
            methodShip$getShipToWorld = MethodHandles.lookup().findVirtual(clazzShip, "getShipToWorld", MethodType.methodType(Matrix4dc.class));
        }
        catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}

