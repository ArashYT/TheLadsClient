/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.entity.player.Inventory
 *  net.minecraft.world.inventory.Slot
 */
package com.thelads.core.features.alwayson.clientsort.compat.stacktnc;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import com.thelads.core.features.alwayson.clientsort.ClientSortClient;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public class StackTncCompat {
    public static final String MOD_NAME = "Stack to Nearby Chests";
    public static final String LOCKED_SLOTS_CLASS = "io.github.xiaocihua.stacktonearbychests.LockedSlots";
    public static final String IS_LOCKED_METHOD = "isLocked";
    public static final Class<?>[] IS_LOCKED_PARAMS = new Class[]{Slot.class};
    private static boolean hasFailed = false;
    private static Method isLockedMethod = null;

    public static boolean isLocked(Slot slot) {
        if (hasFailed) {
            return false;
        }
        if (!(slot.container instanceof Inventory)) {
            return false;
        }
        return StackTncCompat.checkStatic(slot);
    }

    public static boolean checkStatic(Slot slot) {
        try {
            Object result;
            if (isLockedMethod == null) {
                Class<?> clazz = Class.forName(LOCKED_SLOTS_CLASS, false, Thread.currentThread().getContextClassLoader());
                isLockedMethod = clazz.getMethod(IS_LOCKED_METHOD, IS_LOCKED_PARAMS);
            }
            if ((result = isLockedMethod.invoke(null, slot)) instanceof Boolean) {
                Boolean locked = (Boolean)result;
                return locked;
            }
            throw new ClassCastException();
        }
        catch (IllegalAccessException e) {
            ClientSortClient.LOG.info("{} could not be accessed - compat is now disabled: {}", MOD_NAME, e.getMessage());
        }
        catch (ClassNotFoundException e) {
            ClientSortClient.LOG.info("{} did not provide expected class - compat is now disabled: {}", MOD_NAME, e.getMessage());
        }
        catch (NoSuchMethodException e) {
            ClientSortClient.LOG.info("{} did not provide expected method - compat is now disabled: {}", MOD_NAME, e.getMessage());
        }
        catch (ClassCastException e) {
            ClientSortClient.LOG.info("{} did not provide expected return type - compat is now disabled: {}", MOD_NAME, e.getMessage());
        }
        catch (InvocationTargetException e) {
            ClientSortClient.LOG.info("{} threw an exception - compat is now disabled: {}", MOD_NAME, e.getMessage());
        }
        hasFailed = true;
        return false;
    }
}
