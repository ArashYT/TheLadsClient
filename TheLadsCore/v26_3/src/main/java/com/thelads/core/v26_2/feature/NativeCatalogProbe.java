package com.thelads.core.v26_2.feature;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.ModuleSupport;
import org.slf4j.LoggerFactory;

/** Records actual post-registration ownership; catalog counts are not behavior test counts. */
public final class NativeCatalogProbe {
    private NativeCatalogProbe() {}
    public static void log() {
        if (!Boolean.getBoolean("thelads.verifyIntegrations")) return;
        var nativeModules = new JsonArray(); var external = new JsonArray(); var unavailable = new JsonArray();
        for (var module : ModuleManager.getInstance().getModules()) {
            String name = module.getName(); var status = ModuleSupport.get(name);
            if ("Built in".equals(status.label())) nativeModules.add(name);
            else if (ModuleSupport.getExternalId(name) != null) external.add(name);
            else unavailable.add(name);
        }
        var snapshot = new JsonObject(); snapshot.add("native", nativeModules);
        snapshot.add("external", external); snapshot.add("unavailable", unavailable);
        LoggerFactory.getLogger("TheLadsCore").info("Lads capability snapshot: {}", snapshot);
    }
}
