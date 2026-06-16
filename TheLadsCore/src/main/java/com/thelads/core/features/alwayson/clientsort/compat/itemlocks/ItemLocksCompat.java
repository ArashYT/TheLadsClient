/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.entity.player.Inventory
 *  net.minecraft.world.inventory.Slot
 */
package com.thelads.core.features.alwayson.clientsort.compat.itemlocks;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import com.thelads.core.features.alwayson.clientsort.ClientSortClient;
import com.thelads.core.features.alwayson.clientsort.util.inject.ISlot;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public class ItemLocksCompat {
    public static final String MOD_NAME = "ItemLocks";
    public static final String KEY_BINDINGS_CLASS = "com.kirdow.itemlocks.client.input.KeyBindings";
    public static final String IS_BYPASS_METHOD = "isBypass";
    public static final Class<?>[] IS_BYPASS_PARAMS = new Class[0];
    public static final String COMPONENTS_CLASS = "com.kirdow.itemlocks.proxy.Components";
    public static final String GET_COMPONENT_METHOD = "getComponent";
    public static final Class<?>[] GET_COMPONENT_PARAMS = new Class[]{Class.class};
    public static final String LOCK_MANAGER_CLASS = "com.kirdow.itemlocks.client.LockManager";
    public static final String IS_LOCKED_SLOT_RAW_METHOD = "isLockedSlotRaw";
    public static final Class<?>[] IS_LOCKED_SLOT_RAW_PARAMS = new Class[]{Integer.TYPE};
    private static boolean hasFailed = false;
    private static Method isBypassMethod = null;
    private static Object lockManagerInstance = null;
    private static Method isLockedSlotRawMethod = null;

    public static boolean isLocked(Slot slot) {
        if (hasFailed) {
            return false;
        }
        if (!(slot.container instanceof Inventory)) {
            return false;
        }
        int index = ItemLocksCompat.adjustForInventory(((ISlot)slot).clientsort$getIndexInContainer());
        return ItemLocksCompat.checkStatic(index);
    }

    private static int adjustForInventory(int slot) {
        if (0 <= slot && slot <= 8) {
            return slot + 27;
        }
        if (9 <= slot && slot <= 35) {
            return slot - 9;
        }
        return slot;
    }

    public static boolean checkStatic(int index) {
        try {
            Object isLockedResult;
            Object isBypassResult;
            if (isBypassMethod == null) {
                Class<?> keyBindingsClass = Class.forName(KEY_BINDINGS_CLASS, false, Thread.currentThread().getContextClassLoader());
                isBypassMethod = keyBindingsClass.getMethod(IS_BYPASS_METHOD, IS_BYPASS_PARAMS);
            }
            if ((isBypassResult = isBypassMethod.invoke(null, new Object[0])) instanceof Boolean) {
                Boolean bypass = (Boolean)isBypassResult;
                if (bypass.booleanValue()) {
                    return true;
                }
            } else {
                throw new ClassCastException();
            }
            if (lockManagerInstance == null || isLockedSlotRawMethod == null) {
                Class<?> lockManagerClass;
                Class<?> componentsClass = Class.forName(COMPONENTS_CLASS, false, Thread.currentThread().getContextClassLoader());
                Method getComponentMethod = componentsClass.getMethod(GET_COMPONENT_METHOD, GET_COMPONENT_PARAMS);
                lockManagerInstance = getComponentMethod.invoke(null, lockManagerClass = Class.forName(LOCK_MANAGER_CLASS, false, Thread.currentThread().getContextClassLoader()));
                if (lockManagerInstance == null) {
                    throw new ClassCastException();
                }
                isLockedSlotRawMethod = lockManagerClass.getMethod(IS_LOCKED_SLOT_RAW_METHOD, IS_LOCKED_SLOT_RAW_PARAMS);
            }
            if ((isLockedResult = isLockedSlotRawMethod.invoke(lockManagerInstance, index)) instanceof Boolean) {
                Boolean locked = (Boolean)isLockedResult;
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
