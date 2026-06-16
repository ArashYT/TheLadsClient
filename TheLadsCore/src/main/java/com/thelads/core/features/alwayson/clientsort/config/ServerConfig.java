/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.Gson
 *  com.google.gson.GsonBuilder
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package com.thelads.core.features.alwayson.clientsort.config;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.config.ServerClassPolicy;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ServerConfig {
    private static final Path CONFIG_DIR = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir();
    public static final String FILE_NAME = "clientsort-server.json";
    private static final String BACKUP_FILE_NAME = "clientsort-server.unreadable.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public final Options options = new Options();
    private static ServerConfig instance = null;

    public static Options serverOptions() {
        return ServerConfig.get().options;
    }

    private void validate() {
        this.options.classPolicies = Options.classPoliciesValidator.validate(this.options.classPolicies);
    }

    private void upgradeLegacy() {
        if (this.options.validateOperationResults != null) {
            this.options.validateOperationResults = null;
            this.options.classPolicies = Options.classPoliciesDefault.get();
        }
    }

    public static ServerConfig get() {
        if (instance == null) {
            instance = ServerConfig.load();
        }
        return instance;
    }

    public static ServerConfig getAndSave() {
        ServerConfig.get();
        ServerConfig.save();
        return instance;
    }

    public static ServerConfig reloadAndSave() {
        instance = ServerConfig.load();
        ServerConfig.save();
        return instance;
    }

    public static ServerConfig resetAndSave() {
        instance = new ServerConfig();
        ServerConfig.save();
        return instance;
    }

    @NotNull
    public static ServerConfig load() {
        Path file = CONFIG_DIR.resolve(FILE_NAME);
        ServerConfig serverConfig = null;
        if (Files.exists(file, new LinkOption[0])) {
            serverConfig = ServerConfig.load(file, GSON);
            if (serverConfig == null) {
                ServerConfig.backup();
                ClientSort.LOG.warn("Resetting config", new Object[0]);
            } else {
                serverConfig.upgradeLegacy();
            }
        }
        return serverConfig != null ? serverConfig : new ServerConfig();
    }

    @Nullable
    private static ServerConfig load(Path file, Gson gson) {
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file.toFile()), StandardCharsets.UTF_8)) {
            return (ServerConfig)gson.fromJson((Reader)reader, ServerConfig.class);
        } catch (Exception e) {
            ClientSort.LOG.error("Unable to load config", e);
            return null;
        }
    }

    private static void backup() {
        try {
            ClientSort.LOG.warn("Copying {} to {}", FILE_NAME, BACKUP_FILE_NAME);
            if (!Files.isDirectory(CONFIG_DIR, new LinkOption[0])) {
                Files.createDirectories(CONFIG_DIR, new FileAttribute[0]);
            }
            Path file = CONFIG_DIR.resolve(FILE_NAME);
            Path backupFile = file.resolveSibling(BACKUP_FILE_NAME);
            Files.move(file, backupFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (IOException e) {
            ClientSort.LOG.error("Unable to copy config file", e);
        }
    }

    public static void save() {
        if (instance == null) {
            return;
        }
        instance.validate();
        try {
            if (!Files.isDirectory(CONFIG_DIR, new LinkOption[0])) {
                Files.createDirectories(CONFIG_DIR, new FileAttribute[0]);
            }
            Path file = CONFIG_DIR.resolve(FILE_NAME);
            Path tempFile = file.resolveSibling(String.valueOf(file.getFileName()) + ".tmp");
            try (OutputStreamWriter writer = new OutputStreamWriter((OutputStream)new FileOutputStream(tempFile.toFile()), StandardCharsets.UTF_8);){
                writer.write(GSON.toJson((Object)instance));
            }
            catch (IOException e) {
                throw new IOException(e);
            }
            Files.move(tempFile, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            ClientSort.onConfigSaved(instance);
        }
        catch (IOException e) {
            ClientSort.LOG.error("Unable to save config", e);
        }
    }

    public static class Options {
        @Nullable
        public Boolean validateOperationResults;
        @Nullable
        public Boolean validationActiveSingleplayer;
        public static final boolean validationActiveServerDefault = false;
        public boolean validationActiveServer = false;
        public static final boolean validateItemTypeDefault = true;
        public boolean validateItemType = true;
        public static final boolean validateStackSizeDefault = true;
        public boolean validateStackSize = true;
        public static final int validateStackSizeThresholdDefault = 32;
        public int validateStackSizeThreshold = 32;
        public static final boolean alwaysLogUnexpectedResultsDefault = true;
        public boolean alwaysLogUnexpectedResults = true;
        public static final Supplier<List<ServerClassPolicy>> classPoliciesDefaultList = () -> List.of(new ServerClassPolicy("com.simibubi.create.content.equipment.toolbox.ToolboxMenu", false, false, false), new ServerClassPolicy("com.tiviacz.travelersbackpack.inventory.menu.BackpackSettingsMenu", false, false, false), new ServerClassPolicy("com.tom.storagemod.menu.CraftingTerminalMenu", false, false, false), new ServerClassPolicy("com.tom.storagemod.menu.StorageTerminalMenu", false, false, false));
        public static final Supplier<Map<String, ServerClassPolicy>> classPoliciesDefault = () -> {
            LinkedHashMap map = new LinkedHashMap();
            classPoliciesDefaultList.get().forEach(item -> map.put(item.className, item));
            return map;
        };
        public Map<String, ServerClassPolicy> classPolicies = classPoliciesDefault.get();
        public static final Map<String, String> classPolicyKeyUpgradeMap = new HashMap<String, String>();
        public static Validator<Map<String, ServerClassPolicy>> classPoliciesValidator;

        static {
            classPolicyKeyUpgradeMap.put("net.minecraft.class_1661", "net.minecraft.world.entity.player.Inventory");
            classPolicyKeyUpgradeMap.put("net.minecraft.class_1704", "net.minecraft.world.inventory.BeaconMenu");
            classPolicyKeyUpgradeMap.put("net.minecraft.class_1706", "net.minecraft.world.inventory.AnvilMenu");
            classPolicyKeyUpgradeMap.put("net.minecraft.class_1707", "net.minecraft.world.inventory.ChestMenu");
            classPolicyKeyUpgradeMap.put("net.minecraft.class_1708", "net.minecraft.world.inventory.BrewingStandMenu");
            classPolicyKeyUpgradeMap.put("net.minecraft.class_1715", "net.minecraft.world.inventory.TransientCraftingContainer");
            classPolicyKeyUpgradeMap.put("net.minecraft.class_1716", "net.minecraft.world.inventory.DispenserMenu");
            classPolicyKeyUpgradeMap.put("net.minecraft.class_1718", "net.minecraft.world.inventory.EnchantmentMenu");
            classPolicyKeyUpgradeMap.put("net.minecraft.class_1722", "net.minecraft.world.inventory.HopperMenu");
            classPolicyKeyUpgradeMap.put("net.minecraft.class_1724", "net.minecraft.world.inventory.HorseInventoryMenu");
            classPolicyKeyUpgradeMap.put("net.minecraft.class_1726", "net.minecraft.world.inventory.LoomMenu");
            classPolicyKeyUpgradeMap.put("net.minecraft.class_1730", "net.minecraft.world.inventory.PlayerEnderChestContainer");
            classPolicyKeyUpgradeMap.put("net.minecraft.class_1733", "net.minecraft.world.inventory.ShulkerBoxMenu");
            classPolicyKeyUpgradeMap.put("net.minecraft.class_2621", "net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity");
            classPolicyKeyUpgradeMap.put("net.minecraft.class_3705", "net.minecraft.world.inventory.BlastFurnaceMenu");
            classPolicyKeyUpgradeMap.put("net.minecraft.class_3706", "net.minecraft.world.inventory.SmokerMenu");
            classPolicyKeyUpgradeMap.put("net.minecraft.class_3803", "net.minecraft.world.inventory.GrindstoneMenu");
            classPolicyKeyUpgradeMap.put("net.minecraft.class_3858", "net.minecraft.world.inventory.FurnaceMenu");
            classPolicyKeyUpgradeMap.put("net.minecraft.class_3910", "net.minecraft.world.inventory.CartographyTableMenu");
            classPolicyKeyUpgradeMap.put("net.minecraft.class_3971", "net.minecraft.world.inventory.StonecutterMenu");
            classPolicyKeyUpgradeMap.put("net.minecraft.class_4862", "net.minecraft.world.inventory.SmithingMenu");
            classPoliciesValidator = val -> {
                LinkedHashMap validPolicies = new LinkedHashMap();
                if (val == null) {
                    return validPolicies;
                }
                val.values().forEach(cp -> {
                    if (cp != null && cp.className != null && !cp.className.isBlank()) {
                        String className = cp.className;
                        if (classPolicyKeyUpgradeMap.containsKey(className)) {
                            className = classPolicyKeyUpgradeMap.get(className);
                        }
                        validPolicies.put(className, new ServerClassPolicy(className, cp.sortEnabled, cp.stackFillEnabled, cp.transferEnabled, cp.lastAutoEditTime, cp.lastAutoEditReason));
                    }
                });
                LinkedHashMap sortedPolicies = new LinkedHashMap();
                validPolicies.keySet().stream().sorted().forEach(k -> sortedPolicies.put(k, (ServerClassPolicy)validPolicies.get(k)));
                return sortedPolicies;
            };
        }
    }

    @FunctionalInterface
    public static interface Validator<T> {
        @NotNull
        public T validate(@Nullable T var1);
    }
}
