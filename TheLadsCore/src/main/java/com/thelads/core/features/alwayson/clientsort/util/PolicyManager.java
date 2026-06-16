/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.jetbrains.annotations.Nullable
 */
package com.thelads.core.features.alwayson.clientsort.util;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.config.ClassPolicy;
import com.thelads.core.features.alwayson.clientsort.config.Config;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.Nullable;

public class PolicyManager {
    private static final Set<Class<?>> policyClasses = new LinkedHashSet();

    private PolicyManager() {
    }

    public static void reloadPolicyClasses(Set<String> keys) {
        policyClasses.clear();
        for (String key : keys) {
            String className = (String)ClassPolicy.parseKey(key).getFirst();
            try {
                policyClasses.add(Class.forName(className));
            }
            catch (ClassNotFoundException e) {
                if (!ClientSortClient.debug()) continue;
                ClientSortClient.LOG.warn("Unable to load policy class '{}': Class not found.", className);
            }
        }
    }

    @Nullable
    public static ClassPolicy getPolicy(Class<?> cls, String invTitle) {
        ClassPolicy policy = PolicyManager.getPolicyExact(cls, invTitle);
        if (policy != null) {
            return policy;
        }
        Set<Class<?>> matches = policyClasses.stream().filter(c -> c.isAssignableFrom(cls)).collect(Collectors.toSet());
        for (Class<?> c1 : matches) {
            boolean hasSubclass = false;
            for (Class<?> c2 : matches) {
                if (c1.equals(c2) || !c1.isAssignableFrom(c2)) continue;
                hasSubclass = true;
                break;
            }
            if (hasSubclass) continue;
            return PolicyManager.getPolicyExact(c1, invTitle);
        }
        return null;
    }

    @Nullable
    public static ClassPolicy getPolicyExact(Class<?> cls, String invTitle) {
        String clsName = cls.getName();
        ClassPolicy primary = null;
        ClassPolicy secondary = null;
        for (ClassPolicy policy : Config.options().classPolicies.values()) {
            if (!policy.className().equals(clsName)) continue;
            if (policy.invTitle() != null) {
                if (!policy.invTitle().equals(invTitle)) continue;
                primary = policy;
                continue;
            }
            secondary = policy;
        }
        if (primary != null) {
            return primary;
        }
        return secondary;
    }
}
