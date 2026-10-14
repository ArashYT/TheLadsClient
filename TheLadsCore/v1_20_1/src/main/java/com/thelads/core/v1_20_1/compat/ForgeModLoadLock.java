package com.thelads.core.v1_20_1.compat;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Table;
import com.google.common.collect.Tables;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Synchronization primitives and collection-hardening helpers for parallel Forge 1.20.1 mod construction
 * and common setup (Zero SMP).
 */
public final class ForgeModLoadLock {
    public static final ReentrantLock CREATE_LOCK = new ReentrantLock();
    public static final ReentrantLock ZETA_LOCK = new ReentrantLock();

    private ForgeModLoadLock() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void synchronizeRegistrateFields(Object instance) {
        if (instance == null) return;
        Class<?> cls = instance.getClass();
        while (cls != null && !"com.tterrag.registrate.AbstractRegistrate".equals(cls.getName())) {
            cls = cls.getSuperclass();
        }
        if (cls == null) return;
        for (String fieldName : new String[]{"registrations", "datagensByEntry"}) {
            try {
                Field f = cls.getDeclaredField(fieldName);
                f.setAccessible(true);
                Object val = f.get(instance);
                if (val instanceof Table table) {
                    f.set(instance, Tables.synchronizedTable(table));
                }
            } catch (Throwable ignored) {}
        }
        for (String fieldName : new String[]{"registerCallbacks", "afterRegisterCallbacks", "creativeModeTabModifiers"}) {
            try {
                Field f = cls.getDeclaredField(fieldName);
                f.setAccessible(true);
                Object val = f.get(instance);
                if (val instanceof Multimap multimap) {
                    f.set(instance, Multimaps.synchronizedMultimap(multimap));
                }
            } catch (Throwable ignored) {}
        }
        try {
            Field f = cls.getDeclaredField("datagens");
            f.setAccessible(true);
            Object val = f.get(instance);
            if (val instanceof ListMultimap listMultimap) {
                f.set(instance, Multimaps.synchronizedListMultimap(listMultimap));
            }
        } catch (Throwable ignored) {}
        try {
            Field f = cls.getDeclaredField("completedRegistrations");
            f.setAccessible(true);
            Object val = f.get(instance);
            if (val instanceof Set set) {
                f.set(instance, Collections.synchronizedSet(set));
            }
        } catch (Throwable ignored) {}
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void synchronizeMapFields(Object instance, String targetClassName, String... fieldNames) {
        if (instance == null) return;
        Class<?> cls = instance.getClass();
        while (cls != null && !targetClassName.equals(cls.getName())) {
            cls = cls.getSuperclass();
        }
        if (cls == null) return;
        for (String fieldName : fieldNames) {
            try {
                Field f = cls.getDeclaredField(fieldName);
                f.setAccessible(true);
                Object val = f.get(instance);
                if (val instanceof Map map && !(val instanceof ConcurrentHashMap)) {
                    f.set(instance, new ConcurrentHashMap(map));
                }
            } catch (Throwable ignored) {}
        }
    }

    public static Object resolveModEventBus(Object registrateInstance) {
        if (registrateInstance == null) return null;
        try {
            Class<?> cls = registrateInstance.getClass();
            while (cls != null && !"com.tterrag.registrate.AbstractRegistrate".equals(cls.getName())) {
                cls = cls.getSuperclass();
            }
            if (cls == null) return null;
            Field modidField = cls.getDeclaredField("modid");
            modidField.setAccessible(true);
            Object modidObj = modidField.get(registrateInstance);
            if (!(modidObj instanceof String modid) || modid.isEmpty()) return null;

            Class<?> modListClass = Class.forName("net.minecraftforge.fml.ModList");
            Object modList = modListClass.getMethod("get").invoke(null);
            if (modList == null) return null;
            Object optContainer = modListClass.getMethod("getModContainerById", String.class).invoke(modList, modid);
            if (optContainer instanceof java.util.Optional<?> opt && opt.isPresent()) {
                Object container = opt.get();
                try {
                    return container.getClass().getMethod("getEventBus").invoke(container);
                } catch (NoSuchMethodException ignored) {}
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
