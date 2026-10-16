package com.thelads.smp.disabler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.smp.config.LadsConfig;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Comprehensive 10-pathway Item & Content Disabling Engine for The Lads SMP.
 *
 * Pathways enforced:
 * 1. World item pickup & entity tick removal (MixinItemEntity)
 * 2. Hand holding, equipment slots, dispenser equip, and item/block use callbacks (MixinLivingEntity, MixinServerPlayer, Fabric events)
 * 3. Active player inventory & equipment sweep with QUARANTINE / DELETE / REPLACE policies (MixinServerPlayer)
 * 4. Container slot placement, extraction, shift-clicking, and hopper/automation transfers (MixinSlot, MixinAbstractContainerMenu, MixinHopperBlockEntity)
 * 5. Player item dropping & player-to-player transfer prevention (MixinServerPlayer)
 * 6. Command spawning (/give, /item, ItemInput) blocking (MixinItemInput)
 * 7. Vanilla & modded LootTable generation filtering + registry load stripping (MixinLootTable, LadsDataUpgrader)
 * 8. Mob drops, block drops, fishing, and villager/trader offer filtering (MixinEntityDrops, MixinBlockDrops, MixinMerchantOffer)
 * 9. Crafting, smelting, smithing, stonecutting, and modded machine recipe filtering (MixinRecipeMap, MixinCraftingMenu, LadsDataUpgrader)
 * 10. Dynamic wildcard/glob/tag configuration & runtime /ladssmp admin commands
 */
public final class ItemDisablerSystem {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP/Disabler");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static volatile DisabledContentConfig CONFIG = new DisabledContentConfig();
    private static volatile CompiledRuleSet GLOBAL_RULES = CompiledRuleSet.EMPTY;
    private static volatile CompiledRuleSet LOOT_ONLY_RULES = CompiledRuleSet.EMPTY;
    private static volatile CompiledRuleSet RECIPE_ONLY_RULES = CompiledRuleSet.EMPTY;
    private static volatile CompiledRuleSet RECIPE_ID_RULES = CompiledRuleSet.EMPTY;
    private static volatile CompiledRuleSet PROTECTED_BLOCK_RULES = CompiledRuleSet.EMPTY;

    private static final Map<UUID, Long> LAST_PLAYER_NOTIFY_MS = new ConcurrentHashMap<>();

    private ItemDisablerSystem() {}

    public static void init() {
        reload();
        registerInteractionCallbacks();
        LOGGER.info("Initialized Lads SMP ItemDisablerSystem with {} global rules, {} loot rules, {} recipe rules.",
                CONFIG.disabledItems.size(), CONFIG.lootOnlyDisabledItems.size(), CONFIG.recipeOnlyDisabledItems.size());
    }

    public static synchronized void reload() {
        reload(DisabledContentConfig.getDefaultPath());
    }

    public static synchronized void reload(Path configPath) {
        DisabledContentConfig loaded = DisabledContentConfig.load(configPath);
        applyConfig(loaded);
    }

    public static synchronized void applyConfig(DisabledContentConfig config) {
        CONFIG = config;
        GLOBAL_RULES = CompiledRuleSet.compile(config.disabledItems);
        LOOT_ONLY_RULES = CompiledRuleSet.compile(config.lootOnlyDisabledItems);
        RECIPE_ONLY_RULES = CompiledRuleSet.compile(config.recipeOnlyDisabledItems);
        RECIPE_ID_RULES = CompiledRuleSet.compile(config.disabledRecipeIds);
        PROTECTED_BLOCK_RULES = CompiledRuleSet.compile(LadsConfig.get().features.protectedBlockPatterns);
    }

    public static DisabledContentConfig getConfig() {
        return CONFIG;
    }

    public static synchronized boolean addDisabledRule(String rule, String reason) {
        String normalized = normalizeRule(rule);
        if (normalized.isEmpty()) return false;
        if (!CONFIG.disabledItems.contains(normalized)) {
            CONFIG.disabledItems.add(normalized);
            if (reason != null && !reason.isBlank()) {
                CONFIG.disableReasons.put(normalized, reason);
            }
            DisabledContentConfig.save(DisabledContentConfig.getDefaultPath(), CONFIG);
            applyConfig(CONFIG);
            return true;
        }
        return false;
    }

    public static synchronized boolean removeDisabledRule(String rule) {
        String normalized = normalizeRule(rule);
        boolean removed = CONFIG.disabledItems.remove(normalized);
        CONFIG.disableReasons.remove(normalized);
        if (removed) {
            DisabledContentConfig.save(DisabledContentConfig.getDefaultPath(), CONFIG);
            applyConfig(CONFIG);
        }
        return removed;
    }

    public static boolean isIdDisabled(String idString) {
        if (!CONFIG.enabled || idString == null || idString.isEmpty()) return false;
        return GLOBAL_RULES.matchesId(idString);
    }

    public static boolean isIdDisabledForLoot(String idString) {
        if (!CONFIG.enabled || idString == null || idString.isEmpty()) return false;
        return GLOBAL_RULES.matchesId(idString) || LOOT_ONLY_RULES.matchesId(idString);
    }

    public static boolean isIdDisabledForRecipeOutput(String idString) {
        if (!CONFIG.enabled || idString == null || idString.isEmpty()) return false;
        return GLOBAL_RULES.matchesId(idString) || RECIPE_ONLY_RULES.matchesId(idString);
    }

    public static boolean isRecipeIdDisabled(String recipeIdString) {
        if (!CONFIG.enabled || recipeIdString == null || recipeIdString.isEmpty()) return false;
        return RECIPE_ID_RULES.matchesId(recipeIdString);
    }

    public static boolean isProtectedBlockId(String blockIdString) {
        if (!LadsConfig.get().features.carryOnProtection || blockIdString == null || blockIdString.isEmpty()) {
            return false;
        }
        return PROTECTED_BLOCK_RULES.matchesId(blockIdString) || isIdDisabled(blockIdString);
    }

    public static boolean isDisabled(Item item) {
        if (!CONFIG.enabled || item == null || item == Items.AIR) return false;
        if (GLOBAL_RULES.matchesItem(item)) return true;
        if (!GLOBAL_RULES.tagKeys.isEmpty()) {
            ItemStack defaultStack = item.getDefaultInstance();
            for (TagKey<Item> tagKey : GLOBAL_RULES.tagKeys) {
                if (defaultStack.is(tagKey)) return true;
            }
        }
        return false;
    }

    public static boolean isDisabled(ItemStack stack) {
        if (!CONFIG.enabled || stack == null || stack.isEmpty()) return false;
        if (GLOBAL_RULES.matchesItem(stack.getItem())) {
            return true;
        }
        for (TagKey<Item> tagKey : GLOBAL_RULES.tagKeys) {
            if (stack.is(tagKey)) return true;
        }
        return false;
    }

    public static boolean isDisabledForLoot(ItemStack stack) {
        if (!CONFIG.enabled || stack == null || stack.isEmpty()) return false;
        if (isDisabled(stack)) return true;
        if (LOOT_ONLY_RULES.matchesItem(stack.getItem())) {
            return true;
        }
        for (TagKey<Item> tagKey : LOOT_ONLY_RULES.tagKeys) {
            if (stack.is(tagKey)) return true;
        }
        return false;
    }

    public static boolean isDisabledForRecipeOutput(ItemStack stack) {
        if (!CONFIG.enabled || stack == null || stack.isEmpty()) return false;
        if (isDisabled(stack)) return true;
        if (RECIPE_ONLY_RULES.matchesItem(stack.getItem())) {
            return true;
        }
        for (TagKey<Item> tagKey : RECIPE_ONLY_RULES.tagKeys) {
            if (stack.is(tagKey)) return true;
        }
        return false;
    }

    public static boolean shouldBypass(Player player) {
        if (player == null) return false;
        return CONFIG.allowAdminBypass && player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    public static String getItemIdString(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "minecraft:air";
        Identifier key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key != null ? key.toString() : "minecraft:air";
    }

    /**
     * Sweeps a player's entire inventory and equipment slots, enforcing the configured ExistingItemPolicy.
     */
    public static int sanitizePlayerInventory(ServerPlayer player, String triggerSource) {
        if (!CONFIG.enabled || player == null || shouldBypass(player)) return 0;

        int sanitizedCount = 0;
        Inventory inventory = player.getInventory();
        int size = inventory.getContainerSize();
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (isDisabled(stack)) {
                ItemStack replacement = handleDisabledStackFound(player, stack, triggerSource + ":inv_slot_" + slot);
                inventory.setItem(slot, replacement);
                sanitizedCount++;
            }
        }

        for (EquipmentSlot eqSlot : EquipmentSlot.VALUES) {
            ItemStack eqStack = player.getItemBySlot(eqSlot);
            if (isDisabled(eqStack)) {
                ItemStack replacement = handleDisabledStackFound(player, eqStack, triggerSource + ":equip_" + eqSlot.getName());
                player.setItemSlot(eqSlot, replacement);
                sanitizedCount++;
            }
        }

        if (sanitizedCount > 0) {
            inventory.setChanged();
            player.containerMenu.broadcastChanges();
        }
        return sanitizedCount;
    }

    /**
     * Sanitizes any Container (chest, barrel, hopper, modded storage) by applying the ExistingItemPolicy.
     */
    public static int sanitizeContainer(Container container, ServerPlayer actor, String triggerSource) {
        if (!CONFIG.enabled || container == null || (actor != null && shouldBypass(actor))) return 0;
        int sanitizedCount = 0;
        int size = container.getContainerSize();
        for (int i = 0; i < size; i++) {
            ItemStack stack = container.getItem(i);
            if (isDisabled(stack)) {
                ItemStack replacement = handleDisabledStackFound(actor, stack, triggerSource + ":container_slot_" + i);
                container.setItem(i, replacement);
                sanitizedCount++;
            }
        }
        if (sanitizedCount > 0) {
            container.setChanged();
        }
        return sanitizedCount;
    }

    public static ItemStack handleDisabledStackFound(ServerPlayer player, ItemStack disabledStack, String context) {
        String itemId = getItemIdString(disabledStack);
        int count = disabledStack.getCount();
        DisabledContentConfig.ExistingItemPolicy policy = CONFIG.existingItemPolicy;

        String playerName = player != null ? player.getScoreboardName() : "SYSTEM/WORLD";
        String playerUuid = player != null ? player.getStringUUID() : "00000000-0000-0000-0000-000000000000";

        if (policy == DisabledContentConfig.ExistingItemPolicy.QUARANTINE) {
            recordQuarantinedItem(playerUuid, playerName, itemId, count, disabledStack.getComponents().toString(), context);
        }

        ItemStack resultStack = ItemStack.EMPTY;
        if (policy == DisabledContentConfig.ExistingItemPolicy.REPLACE) {
            Identifier repId = Identifier.tryParse(CONFIG.replacementItemId);
            if (repId != null && BuiltInRegistries.ITEM.containsKey(repId)) {
                Item repItem = BuiltInRegistries.ITEM.getValue(repId);
                if (repItem != Items.AIR && !isDisabled(repItem)) {
                    int maxStack = repItem.getDefaultMaxStackSize();
                    resultStack = new ItemStack(repItem, Math.min(count, maxStack));
                }
            }
        }

        writeAuditLog(String.format("[%s] Policy=%s Player=%s(%s) Item=%s Count=%d Context=%s",
                Instant.now(), policy.name(), playerName, playerUuid, itemId, count, context));

        if (player != null && CONFIG.notifyPlayerOnRemoval) {
            notifyPlayerRateLimited(player, itemId, policy);
        }

        return resultStack;
    }

    public static void notifyPlayerRateLimited(ServerPlayer player, String itemId, DisabledContentConfig.ExistingItemPolicy policy) {
        long now = System.currentTimeMillis();
        Long last = LAST_PLAYER_NOTIFY_MS.get(player.getUUID());
        if (last == null || (now - last) > 2500L) {
            LAST_PLAYER_NOTIFY_MS.put(player.getUUID(), now);
            String actionVerb = switch (policy) {
                case QUARANTINE -> "quarantined";
                case DELETE -> "removed";
                case REPLACE -> "replaced";
            };
            player.sendSystemMessage(Component.literal("[Lads SMP] ")
                    .withStyle(ChatFormatting.RED)
                    .append(Component.literal("Disabled item '" + itemId + "' is not permitted and was " + actionVerb + ".")
                            .withStyle(ChatFormatting.GRAY)));
        }
    }

    private static synchronized void recordQuarantinedItem(
            String playerUuid, String playerName, String itemId, int count, String componentsSummary, String context) {
        try {
            Path quarantineDir = getDataDir().resolve("quarantine");
            Files.createDirectories(quarantineDir);
            Path file = quarantineDir.resolve(playerUuid + ".json");
            JsonArray records = new JsonArray();
            if (Files.exists(file)) {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    JsonElement parsed = JsonParser.parseReader(reader);
                    if (parsed != null && parsed.isJsonArray()) {
                        records = parsed.getAsJsonArray();
                    }
                } catch (Exception ignored) {}
            }
            JsonObject entry = new JsonObject();
            entry.addProperty("timestamp", Instant.now().toString());
            entry.addProperty("player", playerName);
            entry.addProperty("uuid", playerUuid);
            entry.addProperty("item", itemId);
            entry.addProperty("count", count);
            entry.addProperty("components", componentsSummary);
            entry.addProperty("context", context);
            records.add(entry);
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(records, writer);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to record quarantined item {} for {}", itemId, playerName, e);
        }
    }

    public static synchronized void writeAuditLog(String line) {
        LOGGER.info(line);
        try {
            Path logFile = getDataDir().resolve("ladssmp-disabler-audit.log");
            Files.createDirectories(logFile.getParent());
            Files.writeString(logFile, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {}
    }

    private static Path getDataDir() {
        try {
            return FabricLoader.getInstance().getConfigDir().resolve("ladssmp");
        } catch (Throwable ignored) {
            return Path.of("build", "test-config", "ladssmp");
        }
    }

    private static void registerInteractionCallbacks() {
        // Pathway 2: Prevent using disabled items in hand
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (isDisabled(stack) && !shouldBypass(player)) {
                if (player instanceof ServerPlayer serverPlayer) {
                    ItemStack replacement = handleDisabledStackFound(serverPlayer, stack, "use_item_" + hand.name());
                    player.setItemInHand(hand, replacement);
                }
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        // Pathway 2 + CarryOn exploit protection on UseBlockCallback
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (isDisabled(stack) && !shouldBypass(player)) {
                if (player instanceof ServerPlayer serverPlayer) {
                    ItemStack replacement = handleDisabledStackFound(serverPlayer, stack, "use_block_" + hand.name());
                    player.setItemInHand(hand, replacement);
                }
                return InteractionResult.FAIL;
            }

            // CarryOn exploit protection: block sneaking empty-handed pickup of spawners, vaults, Lootr chests, waystones
            if (LadsConfig.get().features.carryOnProtection && player.isShiftKeyDown()
                    && player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty()) {
                BlockPos pos = hitResult.getBlockPos();
                BlockState state = world.getBlockState(pos);
                Identifier blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                if (blockId != null && isProtectedBlockId(blockId.toString())) {
                    if (player instanceof ServerPlayer sp) {
                        sp.sendSystemMessage(Component.literal("[Lads SMP] ")
                                .withStyle(ChatFormatting.RED)
                                .append(Component.literal("Carrying block '" + blockId + "' is disabled for server balance.")
                                        .withStyle(ChatFormatting.GRAY)));
                    }
                    return InteractionResult.FAIL;
                }
            }
            return InteractionResult.PASS;
        });

        // Pathway 2: Prevent attacking blocks with a disabled item
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (isDisabled(stack) && !shouldBypass(player)) {
                if (player instanceof ServerPlayer serverPlayer) {
                    ItemStack replacement = handleDisabledStackFound(serverPlayer, stack, "attack_block_" + hand.name());
                    player.setItemInHand(hand, replacement);
                }
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        // Pathway 2 + FriendlyFire Pet Protection on AttackEntityCallback
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (isDisabled(stack) && !shouldBypass(player)) {
                if (player instanceof ServerPlayer serverPlayer) {
                    ItemStack replacement = handleDisabledStackFound(serverPlayer, stack, "attack_entity_" + hand.name());
                    player.setItemInHand(hand, replacement);
                }
                return InteractionResult.FAIL;
            }

            // Consolidated Feature: Friendly Fire Pet Protection
            if (LadsConfig.get().features.friendlyFireProtection && !player.isShiftKeyDown()) {
                if (entity instanceof OwnableEntity ownable && ownable.getOwnerReference() != null) {
                    boolean isOwnPet = player.getUUID().equals(ownable.getOwnerReference().getUUID());
                    if (isOwnPet || LadsConfig.get().features.protectAllPlayersPets) {
                        if (player instanceof ServerPlayer sp && LadsConfig.get().features.showFriendlyFireMessage) {
                            sp.sendSystemMessage(Component.literal("[Lads SMP] ")
                                    .withStyle(ChatFormatting.YELLOW)
                                    .append(Component.literal("Pet protection active! Sneak to attack tamed pets.")
                                            .withStyle(ChatFormatting.GRAY)), true);
                        }
                        return InteractionResult.FAIL;
                    }
                }
            }
            return InteractionResult.PASS;
        });
    }

    public static String normalizeRule(String rawRule) {
        if (rawRule == null) return "";
        String trimmed = rawRule.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) return "";
        if (!trimmed.startsWith("#") && !trimmed.contains(":") && !trimmed.contains("*")) {
            return "minecraft:" + trimmed;
        }
        return trimmed;
    }

    /**
     * Immutable compiled rule matcher supporting exact IDs, wildcard/glob patterns (* and ?), and item #tags.
     * applyConfig compiles a new set on every config change, so the per-Item id verdicts it caches never go stale.
     */
    public static final class CompiledRuleSet {
        public static final CompiledRuleSet EMPTY = new CompiledRuleSet(Set.of(), List.of(), List.of());

        public final Set<String> exactIds;
        public final List<Pattern> globPatterns;
        public final List<TagKey<Item>> tagKeys;
        /** matchesId of each item's registry id, decided once: item entities, loot and slots check every tick. */
        private final Map<Item, Boolean> itemVerdicts = new ConcurrentHashMap<>();

        private CompiledRuleSet(Set<String> exactIds, List<Pattern> globPatterns, List<TagKey<Item>> tagKeys) {
            this.exactIds = exactIds;
            this.globPatterns = globPatterns;
            this.tagKeys = tagKeys;
        }

        public static CompiledRuleSet compile(List<String> rules) {
            if (rules == null || rules.isEmpty()) return EMPTY;
            Set<String> exact = new HashSet<>();
            List<Pattern> patterns = new ArrayList<>();
            List<TagKey<Item>> tags = new ArrayList<>();

            for (String raw : rules) {
                String rule = normalizeRule(raw);
                if (rule.isEmpty()) continue;

                if (rule.startsWith("#")) {
                    String tagIdStr = rule.substring(1);
                    Identifier tagId = Identifier.tryParse(tagIdStr);
                    if (tagId != null) {
                        tags.add(TagKey.create(Registries.ITEM, tagId));
                    }
                } else if (rule.contains("*") || rule.contains("?")) {
                    patterns.add(compileGlob(rule));
                } else {
                    exact.add(rule);
                }
            }
            return new CompiledRuleSet(
                    Collections.unmodifiableSet(exact),
                    Collections.unmodifiableList(patterns),
                    Collections.unmodifiableList(tags)
            );
        }

        /** matchesId(the item's registry id) with no id string, lowercasing or regex after the item's first check (tags are separate). */
        public boolean matchesItem(Item item) {
            if (exactIds.isEmpty() && globPatterns.isEmpty()) return false;
            Boolean verdict = itemVerdicts.get(item);
            if (verdict == null) {
                Identifier key = BuiltInRegistries.ITEM.getKey(item);
                verdict = key != null && matchesId(key.toString());
                itemVerdicts.put(item, verdict);
            }
            return verdict;
        }

        public boolean matchesId(String idString) {
            String normalized = idString.toLowerCase(Locale.ROOT);
            if (!normalized.contains(":")) {
                normalized = "minecraft:" + normalized;
            }
            if (exactIds.contains(normalized)) {
                return true;
            }
            for (Pattern pattern : globPatterns) {
                if (pattern.matcher(normalized).matches()) {
                    return true;
                }
            }
            return false;
        }

        private static Pattern compileGlob(String glob) {
            StringBuilder regex = new StringBuilder("^");
            for (int i = 0; i < glob.length(); i++) {
                char c = glob.charAt(i);
                switch (c) {
                    case '*' -> regex.append(".*");
                    case '?' -> regex.append('.');
                    case '.', '(', ')', '+', '|', '^', '$', '@', '%', '[', ']', '{', '}', '\\' ->
                            regex.append('\\').append(c);
                    default -> regex.append(c);
                }
            }
            regex.append('$');
            return Pattern.compile(regex.toString());
        }
    }
}
