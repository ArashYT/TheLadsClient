/*
 * Decompiled with CFR 0.152.
 */
package fuzs.resourcepackoverrides.common.services;

import java.util.ServiceLoader;

public final class ServiceProviderLoader {
    private ServiceProviderLoader() {
    }

    public static <T> T load(Class<T> clazz) {
        return ServiceLoader.load(clazz, ServiceProviderLoader.class.getClassLoader()).findFirst().orElseThrow(() -> new NullPointerException("Failed to load service for " + clazz.getName()));
    }
}

