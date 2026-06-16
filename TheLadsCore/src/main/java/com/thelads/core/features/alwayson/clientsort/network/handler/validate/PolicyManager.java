/*
 * Decompiled with CFR 0.152.
 */
package com.thelads.core.features.alwayson.clientsort.network.handler.validate;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.config.ServerClassPolicy;
import com.thelads.core.features.alwayson.clientsort.config.ServerConfig;
import com.thelads.core.features.alwayson.clientsort.exception.PayloadHandlerException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PolicyManager {
    private static final Set<Class<?>> policyClasses = new LinkedHashSet();

    private PolicyManager() {
    }

    public static void reloadPolicyClasses(Set<String> classNames) {
        policyClasses.clear();
        for (String className : classNames) {
            try {
                policyClasses.add(Class.forName(className));
            }
            catch (ClassNotFoundException e) {
                if (!ClientSort.debug()) continue;
                ClientSort.LOG.warn("Unable to load policy class '{}': Class not found.", className);
            }
        }
    }

    public static void setPolicy(ServerClassPolicy newPolicy, String opName, String message) {
        ServerClassPolicy policy = ServerConfig.serverOptions().classPolicies.get(newPolicy.className);
        if (policy != null) {
            policy.setFrom(newPolicy);
        } else {
            policy = newPolicy;
            ServerConfig.serverOptions().classPolicies.put(newPolicy.className, newPolicy);
        }
        policy.lastAutoEditTime = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        policy.lastAutoEditReason = message;
        ServerConfig.save();
        ClientSort.LOG.warn("Server-side policy for class '{}' has been updated to ignore payload '{}'. You can change the policy by editing the '{}' config file.", newPolicy.className, opName, "clientsort-server.json");
    }

    public static void checkPolicy(Class<?> cls, Function<ServerClassPolicy, Boolean> op) throws PayloadHandlerException.UnsupportedOpException {
        ServerClassPolicy configClassPolicy = PolicyManager.getClassPolicy(cls);
        if (configClassPolicy != null && !op.apply(configClassPolicy).booleanValue()) {
            throw new PayloadHandlerException.UnsupportedOpException(String.format("Server policy does not allow this operation for class '%s'!", cls.getName()));
        }
    }

    private static ServerClassPolicy getClassPolicy(Class<?> cls) {
        ServerClassPolicy layout = ServerConfig.serverOptions().classPolicies.get(cls.getName());
        if (layout != null) {
            return layout;
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
            return ServerConfig.serverOptions().classPolicies.get(c1.getName());
        }
        return null;
    }
}
