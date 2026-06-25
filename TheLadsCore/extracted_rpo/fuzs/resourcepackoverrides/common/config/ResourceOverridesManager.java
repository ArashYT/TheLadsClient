/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.base.Strings
 *  com.google.common.collect.ImmutableList
 *  com.google.common.collect.ImmutableList$Builder
 *  com.google.common.collect.ImmutableMap
 *  com.google.common.collect.ImmutableMap$Builder
 *  com.google.common.collect.Maps
 *  com.google.gson.JsonArray
 *  com.google.gson.JsonElement
 *  com.google.gson.JsonObject
 *  com.mojang.serialization.DynamicOps
 *  com.mojang.serialization.JsonOps
 *  net.minecraft.core.RegistryAccess
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.ComponentSerialization
 *  net.minecraft.server.packs.repository.Pack$Position
 *  net.minecraft.server.packs.repository.PackCompatibility
 *  net.minecraft.util.GsonHelper
 *  net.minecraft.util.StrictJsonParser
 *  org.apache.commons.compress.utils.Lists
 *  org.jspecify.annotations.Nullable
 */
package fuzs.resourcepackoverrides.common.config;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import fuzs.resourcepackoverrides.common.ResourcePackOverrides;
import fuzs.resourcepackoverrides.common.config.JsonConfigFileUtil;
import fuzs.resourcepackoverrides.common.server.packs.PackSelectionOverride;
import java.io.FileReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.StrictJsonParser;
import org.apache.commons.compress.utils.Lists;
import org.jspecify.annotations.Nullable;

public class ResourceOverridesManager {
    private static final String FILE_NAME = "resourcepackoverrides.json";
    private static final String SCHEMA_VERSION = String.valueOf(2);
    private static final String GROUP_PREFIX = "$$";
    private static Map<String, PackSelectionOverride> overridesById = Maps.newHashMap();
    private static List<String> defaultResourcePacks;
    private static PackSelectionOverride defaultOverride;
    private static int failedReloads;

    public static PackSelectionOverride getOverride(String id) {
        if (defaultOverride == null) {
            ResourceOverridesManager.load();
        }
        return overridesById.getOrDefault(id, defaultOverride);
    }

    public static List<String> getDefaultResourcePacks(boolean failed) {
        if (defaultResourcePacks == null) {
            ResourceOverridesManager.load();
        }
        if (failed && --failedReloads < 0) {
            return ImmutableList.of();
        }
        return defaultResourcePacks;
    }

    public static void load() {
        defaultResourcePacks = ImmutableList.of();
        defaultOverride = PackSelectionOverride.EMPTY;
        JsonConfigFileUtil.getAndLoad(FILE_NAME, file -> {
            JsonObject rootJsonObject = new JsonObject();
            rootJsonObject.addProperty("schema_version", SCHEMA_VERSION);
            JsonObject defaultOverridesJsonObject = new JsonObject();
            defaultOverridesJsonObject.addProperty("force_compatible", Boolean.valueOf(true));
            rootJsonObject.add("default_overrides", (JsonElement)defaultOverridesJsonObject);
            JsonConfigFileUtil.saveToFile(file, (JsonElement)rootJsonObject);
        }, ResourceOverridesManager::deserializeAllOverrides);
    }

    private static void deserializeAllOverrides(FileReader reader) {
        JsonElement jsonElement = (JsonElement)JsonConfigFileUtil.GSON.fromJson((Reader)reader, JsonElement.class);
        JsonObject jsonObject = GsonHelper.convertToJsonObject((JsonElement)jsonElement, (String)"resource pack override");
        String schemaVersion = GsonHelper.getAsString((JsonObject)jsonObject, (String)"schema_version", (String)SCHEMA_VERSION);
        if (!schemaVersion.equals(SCHEMA_VERSION)) {
            ResourcePackOverrides.LOGGER.warn("Outdated config schema! Config might not work correctly. Current schema is {}.", (Object)SCHEMA_VERSION);
        }
        failedReloads = GsonHelper.getAsInt((JsonObject)jsonObject, (String)"failed_reloads_per_session", (int)5);
        if (jsonObject.has("default_packs")) {
            JsonArray resourcePacks = jsonObject.getAsJsonArray("default_packs");
            ImmutableList.Builder builder = ImmutableList.builder();
            for (Object resourcePack : resourcePacks) {
                builder.add((Object)resourcePack.getAsString());
            }
            defaultResourcePacks = builder.build();
        }
        if (jsonObject.has("default_overrides")) {
            defaultOverride = ResourceOverridesManager.deserializeOverrideEntry(jsonObject.get("default_overrides"));
        }
        if (!jsonObject.has("pack_overrides")) {
            return;
        }
        HashMap packOverrides = Maps.newHashMap();
        HashMap overrideGroups = Maps.newHashMap();
        JsonObject overrides = jsonObject.getAsJsonObject("pack_overrides");
        for (Map.Entry entry : overrides.entrySet()) {
            JsonElement packOverride = (JsonElement)entry.getValue();
            if (packOverride.isJsonObject()) {
                packOverrides.put((String)entry.getKey(), ResourceOverridesManager.deserializeOverrideEntry(packOverride));
                continue;
            }
            if (!packOverride.isJsonArray()) continue;
            JsonArray jsonArray = GsonHelper.convertToJsonArray((JsonElement)((JsonElement)entry.getValue()), (String)((String)entry.getKey()));
            List groupIds = overrideGroups.computeIfAbsent((String)entry.getKey(), id -> Lists.newArrayList());
            for (JsonElement groupValue : jsonArray) {
                groupIds.add(groupValue.getAsString());
            }
        }
        ImmutableMap.Builder builder = ImmutableMap.builder();
        String prefix = schemaVersion.equals("1") ? "$" : GROUP_PREFIX;
        for (Map.Entry entry : packOverrides.entrySet()) {
            String id2 = (String)entry.getKey();
            if (id2.startsWith(prefix)) {
                List groupIds = (List)overrideGroups.get(id2.substring(prefix.length()));
                if (groupIds == null) {
                    throw new IllegalArgumentException("Unknown group id %s".formatted(id2));
                }
                for (String groupId : groupIds) {
                    builder.put((Object)groupId, (Object)((PackSelectionOverride)entry.getValue()));
                }
                continue;
            }
            builder.put((Object)id2, (Object)((PackSelectionOverride)entry.getValue()));
        }
        overridesById = builder.build();
    }

    private static PackSelectionOverride deserializeOverrideEntry(JsonElement jsonElement) {
        JsonObject jsonObject = GsonHelper.convertToJsonObject((JsonElement)jsonElement, (String)"resource pack override");
        Component title = ResourceOverridesManager.getOptionalString(jsonObject, "title", ResourceOverridesManager::parseComponent);
        Component description = ResourceOverridesManager.getOptionalString(jsonObject, "description", ResourceOverridesManager::parseComponent);
        Pack.Position defaultPosition = ResourceOverridesManager.getOptionalString(jsonObject, "default_position", s -> {
            try {
                return Pack.Position.valueOf((String)s.toUpperCase(Locale.ROOT));
            }
            catch (Exception ignored) {
                return null;
            }
        });
        PackCompatibility compatible = GsonHelper.getAsBoolean((JsonObject)jsonObject, (String)"force_compatible", (boolean)false) ? PackCompatibility.COMPATIBLE : null;
        Boolean fixedPosition = ResourceOverridesManager.getOptionalFlag(jsonObject, "fixed_position");
        Boolean required = GsonHelper.getAsBoolean((JsonObject)jsonObject, (String)"required", (boolean)false) ? Boolean.valueOf(true) : null;
        Boolean hidden = GsonHelper.getAsBoolean((JsonObject)jsonObject, (String)"hidden", (boolean)false) ? Boolean.valueOf(true) : null;
        return new PackSelectionOverride(title, description, defaultPosition, compatible, fixedPosition, required, hidden);
    }

    private static @Nullable Component parseComponent(String json) {
        if (!Strings.isNullOrEmpty((String)json)) {
            JsonElement jsonElement = StrictJsonParser.parse((String)json);
            return ComponentSerialization.CODEC.parse((DynamicOps)RegistryAccess.EMPTY.createSerializationContext((DynamicOps)JsonOps.INSTANCE), (Object)jsonElement).resultOrPartial().orElse(null);
        }
        return null;
    }

    private static <T> @Nullable T getOptionalString(JsonObject jsonObject, String memberName, Function<String, T> converter) {
        return jsonObject.has(memberName) ? (T)converter.apply(GsonHelper.getAsString((JsonObject)jsonObject, (String)memberName)) : null;
    }

    private static @Nullable Boolean getOptionalFlag(JsonObject jsonObject, String memberName) {
        return jsonObject.has(memberName) ? Boolean.valueOf(jsonObject.get(memberName).getAsBoolean()) : null;
    }
}

