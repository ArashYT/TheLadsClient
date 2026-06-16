/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.Gson
 *  com.google.gson.GsonBuilder
 *  net.minecraft.world.entity.player.Inventory
 *  net.minecraft.world.inventory.ChestMenu
 *  net.minecraft.world.inventory.HopperMenu
 *  net.minecraft.world.inventory.HorseInventoryMenu
 *  net.minecraft.world.inventory.PlayerEnderChestContainer
 *  net.minecraft.world.inventory.ShulkerBoxMenu
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package com.thelads.core.features.alwayson.clientsort.config;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;
import com.thelads.core.features.alwayson.clientsort.config.ClassPolicy;
import com.thelads.core.features.alwayson.clientsort.config.Operation;
import com.thelads.core.features.alwayson.clientsort.config.Policy;
import com.thelads.core.features.alwayson.clientsort.config.Vec2i;
import com.thelads.core.features.alwayson.clientsort.config.legacy.ButtonLayout;
import com.thelads.core.features.alwayson.clientsort.order.SortOrder;
import com.thelads.core.features.alwayson.clientsort.config.ServerConfig;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Supplier;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.HorseInventoryMenu;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Config {
    private static final Path CONFIG_DIR = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir();
    private static final String FILE_NAME = "clientsort.json";
    private static final String BACKUP_FILE_NAME = "clientsort.unreadable.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public final Options options = new Options();
    private static Config instance = null;

    public static Options options() {
        return Config.get().options;
    }

    private static int unbox(@Nullable Integer val) {
        return val != null ? val : 0;
    }

    private static float unbox(@Nullable Float val) {
        return val != null ? val.floatValue() : 0.0f;
    }

    private void validate() {
        this.options.interactionInterval = Options.interactionIntervalValidator.validate(this.options.interactionInterval);
        this.options.autoOpDelayPlayer = Options.autoOpDelayValidator.validate(this.options.autoOpDelayPlayer);
        this.options.autoOpDelayContainer = Options.autoOpDelayValidator.validate(this.options.autoOpDelayContainer);
        this.options.hotbarScope = Options.hotbarScopeValidator.validate(this.options.hotbarScope);
        this.options.extraSlotScope = Options.extraSlotScopeValidator.validate(this.options.extraSlotScope);
        this.options.typeMatchTags = Options.typeMatchTagsValidator.validate(this.options.typeMatchTags);
        this.options.sortOrderStr = Options.sortOrderStrValidator.validate(this.options.sortOrderStr);
        this.options.shiftSortOrderStr = Options.shiftSortOrderStrValidator.validate(this.options.shiftSortOrderStr);
        this.options.ctrlSortOrderStr = Options.ctrlSortOrderStrValidator.validate(this.options.ctrlSortOrderStr);
        this.options.altSortOrderStr = Options.altSortOrderStrValidator.validate(this.options.altSortOrderStr);
        this.options.startOverrideItems = Options.startOverrideItemsValidator.validate(this.options.startOverrideItems);
        this.options.endOverrideItems = Options.endOverrideItemsValidator.validate(this.options.endOverrideItems);
        this.options.interactionSound = Options.interactionSoundValidator.validate(this.options.interactionSound);
        this.options.soundInterval = Options.soundIntervalValidator.validate(this.options.soundInterval);
        this.options.soundPitchMin = Options.soundPitchMinValidator.validate(Float.valueOf(this.options.soundPitchMin), this.options).floatValue();
        this.options.soundPitchMax = Options.soundPitchMaxValidator.validate(Float.valueOf(this.options.soundPitchMax), this.options).floatValue();
        this.options.soundVolume = Options.soundVolumeValidator.validate(Float.valueOf(this.options.soundVolume)).floatValue();
        this.options.firstButtonOp = Options.firstButtonOpValidator.validate(this.options.firstButtonOp, this.options);
        this.options.secondButtonOp = Options.secondButtonOpValidator.validate(this.options.secondButtonOp, this.options);
        this.options.thirdButtonOp = Options.thirdButtonOpValidator.validate(this.options.thirdButtonOp, this.options);
        this.options.fourthButtonOp = Options.fourthButtonOpValidator.validate(this.options.fourthButtonOp, this.options);
        this.options.layoutOffset = Options.layoutOffsetValidator.validate(this.options.layoutOffset);
        this.options.classPolicies = Options.classPoliciesValidator.validate(this.options.classPolicies);
    }

    private void upgradeLegacy() {
        if (this.options.buttonLayouts != null && !this.options.buttonLayouts.isEmpty()) {
            this.options.buttonLayouts.values().forEach(bl -> this.options.classPolicies.put(ClassPolicy.getKey(bl.className(), null), new ClassPolicy(bl.className(), null, bl.offset(), false, Boolean.TRUE.equals(bl.sortEnabled()) ? Policy.KEYBIND_BUTTON : Policy.KEYBIND, Boolean.TRUE.equals(bl.stackFillEnabled()) ? Policy.KEYBIND_BUTTON : Policy.KEYBIND, Policy.KEYBIND, Boolean.TRUE.equals(bl.transferEnabled()) ? Policy.KEYBIND_BUTTON : Policy.KEYBIND, null, false, new TreeSet<Integer>())));
            this.options.classPolicies = Options.classPoliciesValidator.validate(this.options.classPolicies);
        }
        this.options.buttonLayouts = null;
    }

    public static Config get() {
        if (instance == null) {
            instance = Config.load();
        }
        return instance;
    }

    public static Config getAndSave() {
        Config.get();
        Config.save();
        return instance;
    }

    public static Config resetAndSave() {
        instance = new Config();
        Config.save();
        return instance;
    }

    @NotNull
    public static Config load() {
        Path file = CONFIG_DIR.resolve(FILE_NAME);
        Config config = null;
        if (Files.exists(file, new LinkOption[0])) {
            config = Config.load(file, GSON);
            if (config == null) {
                Config.backup();
                ClientSortClient.LOG.warn("Resetting config", new Object[0]);
            } else {
                config.upgradeLegacy();
            }
        }
        return config != null ? config : new Config();
    }

    @Nullable
    private static Config load(Path file, Gson gson) {
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file.toFile()), StandardCharsets.UTF_8)) {
            return (Config)gson.fromJson((Reader)reader, Config.class);
        } catch (Exception e) {
            ClientSortClient.LOG.error("Unable to load config", e);
            return null;
        }
    }

    private static void backup() {
        try {
            ClientSortClient.LOG.warn("Copying {} to {}", FILE_NAME, BACKUP_FILE_NAME);
            if (!Files.isDirectory(CONFIG_DIR, new LinkOption[0])) {
                Files.createDirectories(CONFIG_DIR, new FileAttribute[0]);
            }
            Path file = CONFIG_DIR.resolve(FILE_NAME);
            Path backupFile = file.resolveSibling(BACKUP_FILE_NAME);
            Files.move(file, backupFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (IOException e) {
            ClientSortClient.LOG.error("Unable to copy config file", e);
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
            ClientSort.afterConfigSaved(instance);
        }
        catch (IOException e) {
            ClientSortClient.LOG.error("Unable to save config", e);
        }
    }

    public static class Options {
        public static final int INTERACTION_INTERVAL_MIN = 1;
        public static final int INTERACTION_INTERVAL_MAX = 1000;
        public static final int interactionIntervalDefault = 10;
        public int interactionInterval = 10;
        public static Validator<Integer> interactionIntervalValidator = val -> Math.clamp((long)Config.unbox(val), 1, 1000);
        public static final boolean useServerAccelerationDefault = true;
        public boolean useServerAcceleration = true;
        public static final boolean useClientFallbackDefault = false;
        public boolean useClientFallback = false;
        public static final boolean optimizeCreativeSortingDefault = true;
        public boolean optimizeCreativeSorting = true;
        public static final int AUTO_OP_DELAY_MIN = 0;
        public static final int AUTO_OP_DELAY_MAX = 40;
        public static Validator<Integer> autoOpDelayValidator = val -> Math.clamp((long)Config.unbox(val), 0, 40);
        public static final int autoOpDelayPlayerDefault = 2;
        public int autoOpDelayPlayer = 2;
        public static final int autoOpDelayContainerDefault = 2;
        public int autoOpDelayContainer = 2;
        public static final HotbarScope hotbarScopeDefault = HotbarScope.HOTBAR;
        public HotbarScope hotbarScope = hotbarScopeDefault;
        public static Validator<HotbarScope> hotbarScopeValidator = val -> val != null && Arrays.stream(HotbarScope.values()).toList().contains(val) ? val : hotbarScopeDefault;
        public static final ExtraSlotScope extraSlotScopeDefault = ExtraSlotScope.NONE;
        public ExtraSlotScope extraSlotScope = extraSlotScopeDefault;
        public static Validator<ExtraSlotScope> extraSlotScopeValidator = val -> val != null && Arrays.stream(ExtraSlotScope.values()).toList().contains(val) ? val : extraSlotScopeDefault;
        public static final boolean bundlesUseRightClickDefault = false;
        public boolean bundlesUseRightClick = false;
        public static final boolean transferReverseOrderDefault = true;
        public boolean transferReverseOrder = true;
        public static final boolean alwaysMatchByTypeDefault = false;
        public boolean alwaysMatchByType = false;
        public static final Supplier<List<String>> typeMatchTagsDefault = () -> List.of("enchantable/weapon", "enchantable/mining", "enchantable/armor");
        public List<String> typeMatchTags = typeMatchTagsDefault.get();
        public static Validator<List<String>> typeMatchTagsValidator = val -> val != null ? val : typeMatchTagsDefault.get();
        public final transient HashSet<Item> typeMatchItemCache = new HashSet();
        public static final String sortOrderStrDefault = SortOrder.CREATIVE.name;
        public String sortOrderStr = sortOrderStrDefault;
        public static Validator<String> sortOrderStrValidator = val -> val != null && SortOrder.SORT_ORDERS.containsKey(val) ? val : sortOrderStrDefault;
        public transient SortOrder sortOrder;
        public static final String shiftSortOrderStrDefault = SortOrder.QUANTITY.name;
        public String shiftSortOrderStr = shiftSortOrderStrDefault;
        public static Validator<String> shiftSortOrderStrValidator = val -> val != null && SortOrder.SORT_ORDERS.containsKey(val) ? val : shiftSortOrderStrDefault;
        public transient SortOrder shiftSortOrder;
        public static final String ctrlSortOrderStrDefault = SortOrder.ALPHABET.name;
        public String ctrlSortOrderStr = ctrlSortOrderStrDefault;
        public static Validator<String> ctrlSortOrderStrValidator = val -> val != null && SortOrder.SORT_ORDERS.containsKey(val) ? val : ctrlSortOrderStrDefault;
        public transient SortOrder ctrlSortOrder;
        public static final String altSortOrderStrDefault = SortOrder.RAW_ID.name;
        public String altSortOrderStr = altSortOrderStrDefault;
        public static Validator<String> altSortOrderStrValidator = val -> val != null && SortOrder.SORT_ORDERS.containsKey(val) ? val : altSortOrderStrDefault;
        public transient SortOrder altSortOrder;
        public static final boolean useStartOverridesDefault = true;
        public boolean useStartOverrides = true;
        public static final Supplier<List<String>> startOverrideItemsDefault = List::of;
        public List<String> startOverrideItems = startOverrideItemsDefault.get();
        public static Validator<List<String>> startOverrideItemsValidator = val -> val != null ? val : startOverrideItemsDefault.get();
        public final transient Map<Item, Integer> startOverrideMap = new HashMap<Item, Integer>();
        public static final boolean useEndOverridesDefault = true;
        public boolean useEndOverrides = true;
        public static final Supplier<List<String>> endOverrideItemsDefault = List::of;
        public List<String> endOverrideItems = endOverrideItemsDefault.get();
        public static Validator<List<String>> endOverrideItemsValidator = val -> val != null ? val : endOverrideItemsDefault.get();
        public final transient Map<Item, Integer> endOverrideMap = new HashMap<Item, Integer>();
        public static final boolean playSoundSortDefault = false;
        public boolean playSoundSort = false;
        public static final boolean playSoundOtherDefault = false;
        public boolean playSoundOther = false;
        public static final String interactionSoundDefault = "minecraft:block.note_block.xylophone";
        public String interactionSound = "minecraft:block.note_block.xylophone";
        public static Validator<String> interactionSoundValidator = val -> val != null && Identifier.tryParse(val) != null ? val : interactionSoundDefault;
        @Nullable
        public transient Identifier sortSoundLoc = null;
        public static final int SOUND_INTERVAL_MIN = 1;
        public static final int SOUND_INTERVAL_MAX = 100;
        public static final int soundIntervalDefault = 1;
        public int soundInterval = 1;
        public static Validator<Integer> soundIntervalValidator = val -> Math.clamp((long)Config.unbox(val), 1, 100);
        public static final float SOUND_PITCH_MIN = 0.5f;
        public static final float SOUND_PITCH_MAX = 2.0f;
        public static final float soundPitchMinDefault = 0.5f;
        public float soundPitchMin = 0.5f;
        public static AwareValidator<Float> soundPitchMinValidator = (val, options) -> Float.valueOf(Math.clamp(Config.unbox(val), 0.5f, Math.clamp(options.soundPitchMax, 0.5f, 2.0f)));
        public static final float soundPitchMaxDefault = 2.0f;
        public float soundPitchMax = 2.0f;
        public static AwareValidator<Float> soundPitchMaxValidator = (val, options) -> Float.valueOf(Math.clamp(Config.unbox(val), Math.clamp(options.soundPitchMin, 0.5f, 2.0f), 2.0f));
        public static final float SOUND_VOLUME_MIN = 0.0f;
        public static final float SOUND_VOLUME_MAX = 1.0f;
        public static final float soundVolumeDefault = 0.2f;
        public float soundVolume = 0.2f;
        public static Validator<Float> soundVolumeValidator = val -> Float.valueOf(Math.clamp(Config.unbox(val), 0.0f, 1.0f));
        public static final boolean allowSoundOverlapDefault = true;
        public boolean allowSoundOverlap = true;
        public static final boolean showButtonsDefault = true;
        public boolean showButtons = true;
        public static final boolean showButtonTooltipsDefault = false;
        public boolean showButtonTooltips = false;
        public static final boolean anchorButtonsLeftDefault = false;
        public boolean anchorButtonsLeft = false;
        public static final boolean justifyButtonsTopLeftDefault = true;
        public boolean justifyButtonsTopLeft = true;
        public static final boolean buttonsHorizontalDefault = false;
        public boolean buttonsHorizontal = false;
        public static final Operation firstButtonOpDefault = Operation.SORT;
        public Operation firstButtonOp = firstButtonOpDefault;
        public static AwareValidator<Operation> firstButtonOpValidator = (val, options) -> Options.validateUniqueOp(val, options.secondButtonOp, options.thirdButtonOp, options.fourthButtonOp);
        public static final Operation secondButtonOpDefault = Operation.STACK_FILL;
        public Operation secondButtonOp = secondButtonOpDefault;
        public static AwareValidator<Operation> secondButtonOpValidator = (val, options) -> Options.validateUniqueOp(val, options.firstButtonOp, options.thirdButtonOp, options.fourthButtonOp);
        public static final Operation thirdButtonOpDefault = Operation.MATCH_TRANSFER;
        public Operation thirdButtonOp = thirdButtonOpDefault;
        public static AwareValidator<Operation> thirdButtonOpValidator = (val, options) -> Options.validateUniqueOp(val, options.firstButtonOp, options.secondButtonOp, options.fourthButtonOp);
        public static final Operation fourthButtonOpDefault = Operation.TRANSFER;
        public Operation fourthButtonOp = fourthButtonOpDefault;
        public static AwareValidator<Operation> fourthButtonOpValidator = (val, options) -> Options.validateUniqueOp(val, options.firstButtonOp, options.secondButtonOp, options.thirdButtonOp);
        public static final Vec2i layoutOffsetDefault = new Vec2i(-4, 0);
        public Vec2i layoutOffset = layoutOffsetDefault;
        public static Validator<Vec2i> layoutOffsetValidator = val -> val != null ? val : layoutOffsetDefault;
        public static final Supplier<List<ClassPolicy>> classPoliciesDefaultList = () -> List.of(new ClassPolicy(Inventory.class.getName(), null, null, false, Policy.KEYBIND_BUTTON, Policy.KEYBIND, Policy.KEYBIND_BUTTON, Policy.KEYBIND_BUTTON, null, false, new TreeSet<Integer>()), new ClassPolicy(ChestMenu.class.getName(), null, null, false, Policy.KEYBIND_BUTTON, Policy.KEYBIND_BUTTON, Policy.KEYBIND_BUTTON, Policy.KEYBIND_BUTTON, null, false, new TreeSet<Integer>()), new ClassPolicy(HopperMenu.class.getName(), null, null, false, Policy.KEYBIND, Policy.KEYBIND, Policy.KEYBIND_BUTTON, Policy.KEYBIND_BUTTON, null, false, new TreeSet<Integer>()), new ClassPolicy(HorseInventoryMenu.class.getName(), null, null, false, Policy.KEYBIND_BUTTON, Policy.KEYBIND, Policy.KEYBIND, Policy.KEYBIND_BUTTON, null, false, new TreeSet<Integer>()), new ClassPolicy(PlayerEnderChestContainer.class.getName(), null, null, false, Policy.KEYBIND_BUTTON, Policy.KEYBIND_BUTTON, Policy.KEYBIND_BUTTON, Policy.KEYBIND_BUTTON, null, false, new TreeSet<Integer>()), new ClassPolicy(ShulkerBoxMenu.class.getName(), null, null, false, Policy.KEYBIND_BUTTON, Policy.KEYBIND_BUTTON, Policy.KEYBIND_BUTTON, Policy.KEYBIND_BUTTON, null, false, new TreeSet<Integer>()), new ClassPolicy(RandomizableContainerBlockEntity.class.getName(), null, null, false, Policy.KEYBIND_BUTTON, Policy.KEYBIND_BUTTON, Policy.KEYBIND_BUTTON, Policy.KEYBIND_BUTTON, null, false, new TreeSet<Integer>()), new ClassPolicy("com.simibubi.create.content.equipment.toolbox.ToolboxMenu", null, null, false, Policy.NONE, Policy.NONE, Policy.NONE, Policy.NONE, null, false, new TreeSet<Integer>()), new ClassPolicy("com.tiviacz.travelersbackpack.inventory.menu.BackpackSettingsMenu", null, null, false, Policy.NONE, Policy.NONE, Policy.NONE, Policy.NONE, null, false, new TreeSet<Integer>()), new ClassPolicy("com.tom.storagemod.menu.CraftingTerminalMenu", null, null, false, Policy.NONE, Policy.NONE, Policy.NONE, Policy.NONE, null, false, new TreeSet<Integer>()), new ClassPolicy("com.tom.storagemod.menu.StorageTerminalMenu", null, null, false, Policy.NONE, Policy.NONE, Policy.NONE, Policy.NONE, null, false, new TreeSet<Integer>()));
        public static final Supplier<Map<String, ClassPolicy>> classPoliciesDefault = () -> {
            LinkedHashMap map = new LinkedHashMap();
            classPoliciesDefaultList.get().forEach(policy -> map.put(policy.getKey(), policy));
            return map;
        };
        public Map<String, ClassPolicy> classPolicies = classPoliciesDefault.get();
        public static Validator<Policy> policyValidator = val -> val != null && Arrays.stream(Policy.values()).toList().contains(val) ? val : Policy.NONE;
        public static Validator<Map<String, ClassPolicy>> classPoliciesValidator = val -> {
            LinkedHashMap validPolicies = new LinkedHashMap();
            if (val == null) {
                return validPolicies;
            }
            val.values().forEach(cp -> {
                if (cp != null && cp.className() != null && !cp.className().isBlank()) {
                    String className = cp.className();
                    if (ServerConfig.Options.classPolicyKeyUpgradeMap.containsKey(className)) {
                        className = ServerConfig.Options.classPolicyKeyUpgradeMap.get(className);
                    }
                    validPolicies.put(className, new ClassPolicy(className, cp.invTitle(), cp.buttonOffset(), cp.offsetFromSlot(), policyValidator.validate(cp.sortPolicy()), policyValidator.validate(cp.stackFillPolicy()), policyValidator.validate(cp.matchTransferPolicy()), policyValidator.validate(cp.transferPolicy()), cp.autoOp(), cp.autoOpOther(), cp.ignoredSlots() == null ? new TreeSet<Integer>() : cp.ignoredSlots()));
                }
            });
            LinkedHashMap sortedPolicies = new LinkedHashMap();
            validPolicies.keySet().stream().sorted().forEach(k -> sortedPolicies.put(k, (ClassPolicy)validPolicies.get(k)));
            return sortedPolicies;
        };
        @Nullable
        public Map<String, ButtonLayout> buttonLayouts;

        private static Operation validateUniqueOp(@Nullable Operation val, Operation ... others) {
            if (others.length >= Operation.values().length) {
                throw new IllegalArgumentException();
            }
            HashSet<Operation> ops = new HashSet<Operation>(Arrays.stream(Operation.values()).toList());
            if (val != null && ops.contains((Object)val) && !Arrays.stream(others).toList().contains((Object)val)) {
                return val;
            }
            Arrays.stream(others).forEach(ops::remove);
            return (Operation)((Object)ops.stream().findAny().get());
        }

        public static enum HotbarScope {
            HOTBAR,
            INVENTORY,
            NONE;

        }

        public static enum ExtraSlotScope {
            EXTRA,
            HOTBAR,
            INVENTORY,
            NONE;

        }
    }

    @FunctionalInterface
    public static interface Validator<T> {
        @NotNull
        public T validate(@Nullable T var1);
    }

    @FunctionalInterface
    public static interface AwareValidator<T> {
        @NotNull
        public T validate(@Nullable T var1, @NotNull Options var2);
    }
}
