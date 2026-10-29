package com.thelads.smp.command;

import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.thelads.smp.compat.LadsDataUpgrader;
import com.thelads.smp.config.LadsConfig;
import com.thelads.smp.disabler.DisabledContentConfig;
import com.thelads.smp.disabler.ItemDisablerSystem;
import com.thelads.smp.features.ConsolidatedFeatures;
import com.thelads.smp.features.DeathBackupManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

import java.util.List;

/**
 * Registers the /ladssmp command suite for runtime item disabler management,
 * 26.3 data upgrader diagnostics, config hot-reloading, and death backup recovery.
 */
public final class LadsCommands {
    private LadsCommands() {}

    public static void init() {
        CommandRegistrationCallback.EVENT.register(LadsCommands::register);
    }

    private static void register(
            CommandDispatcher<CommandSourceStack> dispatcher,
            CommandBuildContext registryAccess,
            Commands.CommandSelection environment) {
        dispatcher.register(Commands.literal("ladssmp")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.literal("status").executes(LadsCommands::executeStatus))
                .then(Commands.literal("reload").executes(LadsCommands::executeReload))
                .then(Commands.literal("list").executes(LadsCommands::executeList))
                .then(Commands.literal("sweep").executes(LadsCommands::executeSweep))
                .then(Commands.literal("check")
                        .then(Commands.argument("itemId", StringArgumentType.greedyString())
                                .executes(ctx -> executeCheck(ctx, StringArgumentType.getString(ctx, "itemId")))))
                .then(Commands.literal("disable")
                        .then(Commands.argument("rule", StringArgumentType.string())
                                .executes(ctx -> executeDisable(ctx, StringArgumentType.getString(ctx, "rule"), "Added via /ladssmp disable"))
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(ctx -> executeDisable(
                                                ctx,
                                                StringArgumentType.getString(ctx, "rule"),
                                                StringArgumentType.getString(ctx, "reason"))))))
                .then(Commands.literal("enable")
                        .then(Commands.argument("rule", StringArgumentType.greedyString())
                                .executes(ctx -> executeEnable(ctx, StringArgumentType.getString(ctx, "rule")))))
                .then(Commands.literal("deathbackup")
                        .then(Commands.literal("list")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> executeDeathList(ctx, EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("restore")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> executeDeathRestore(ctx, EntityArgument.getPlayer(ctx, "player"), 0))
                                        .then(Commands.argument("index", IntegerArgumentType.integer(0, 50))
                                                .executes(ctx -> executeDeathRestore(
                                                        ctx,
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        IntegerArgumentType.getInteger(ctx, "index"))))))));
    }

    private static int executeStatus(CommandContext<CommandSourceStack> ctx) {
        DisabledContentConfig disablerCfg = ItemDisablerSystem.getConfig();
        LadsConfig cfg = LadsConfig.get();

        ctx.getSource().sendSuccess(() -> Component.literal("=== The Lads SMP Core Status ===")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                "Item Disabler: %s (Policy: %s | Global Rules: %d | Loot Rules: %d | Recipe Rules: %d)",
                disablerCfg.enabled ? "ACTIVE" : "DISABLED",
                disablerCfg.existingItemPolicy.name(),
                disablerCfg.disabledItems.size(),
                disablerCfg.lootOnlyDisabledItems.size(),
                disablerCfg.recipeOnlyDisabledItems.size()))
                .withStyle(ChatFormatting.GREEN), false);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                "26.3 Data Upgrader: Upgraded %d loot tables (%d conditions), %d modded recipes | Stripped %d loot entries, %d recipes",
                LadsDataUpgrader.UPGRADED_LOOT_TABLES.get(),
                LadsDataUpgrader.UPGRADED_LOOT_CONDITIONS.get(),
                LadsDataUpgrader.UPGRADED_RECIPES.get(),
                LadsDataUpgrader.FILTERED_LOOT_ENTRIES.get(),
                LadsDataUpgrader.FILTERED_RECIPES.get()))
                .withStyle(ChatFormatting.AQUA), false);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                "Consolidated Modules: AlwaysEat=%s, NoTemptDelay=%s, SpawnerControl=%s, CryingPortals=%s, EggHatch=%s, DoubleDoors=%s, PetProtect=%s, AttrFix=%s, DeathBackup=%s",
                cfg.features.alwaysEat, cfg.features.noAnimalTemptDelay, cfg.features.betterSpawnerControl,
                cfg.features.cryingPortals, cfg.features.despawningEggsHatch, cfg.features.doubleDoors,
                cfg.features.friendlyFireProtection, cfg.features.attributeFix, cfg.features.deathBackup))
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int executeReload(CommandContext<CommandSourceStack> ctx) {
        LadsConfig.load();
        com.thelads.smp.features.arise.nb8.AriseNB8Config.load();
        boolean disablerOk = ItemDisablerSystem.reload();
        ConsolidatedFeatures.applyAttributeFixes();
        if (!disablerOk) {
            ctx.getSource().sendFailure(Component.literal("[Lads SMP] disabled_items.json failed to parse (backed up to .broken-*); keeping active rules."));
            return 0;
        }

        int swept = 0;
        for (ServerPlayer player : ctx.getSource().getServer().getPlayerList().getPlayers()) {
            swept += ItemDisablerSystem.sanitizePlayerInventory(player, "command_reload");
        }
        int finalSwept = swept;
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[Lads SMP] Reloaded config.json and disabled_items.json (" + finalSwept + " disabled stacks sanitized).")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int executeList(CommandContext<CommandSourceStack> ctx) {
        DisabledContentConfig cfg = ItemDisablerSystem.getConfig();
        ctx.getSource().sendSuccess(() -> Component.literal("=== Disabled Content Rules (" + cfg.disabledItems.size() + ") ===")
                .withStyle(ChatFormatting.GOLD), false);
        for (String rule : cfg.disabledItems) {
            String reason = cfg.disableReasons.getOrDefault(rule, "Disabled by server configuration");
            ctx.getSource().sendSuccess(() -> Component.literal(" - " + rule + " : ")
                    .withStyle(ChatFormatting.RED)
                    .append(Component.literal(reason).withStyle(ChatFormatting.GRAY)), false);
        }
        return cfg.disabledItems.size();
    }

    private static int executeSweep(CommandContext<CommandSourceStack> ctx) {
        int total = 0;
        for (ServerPlayer player : ctx.getSource().getServer().getPlayerList().getPlayers()) {
            total += ItemDisablerSystem.sanitizePlayerInventory(player, "admin_sweep");
        }
        int finalTotal = total;
        ctx.getSource().sendSuccess(() -> Component.literal("[Lads SMP] Swept all online player inventories; sanitized " + finalTotal + " stack(s).")
                .withStyle(ChatFormatting.GREEN), true);
        return finalTotal;
    }

    private static int executeCheck(CommandContext<CommandSourceStack> ctx, String itemId) {
        String norm = LadsDataUpgrader.normalizeId(itemId);
        boolean global = ItemDisablerSystem.isIdDisabled(norm);
        boolean loot = ItemDisablerSystem.isIdDisabledForLoot(norm);
        boolean recipe = ItemDisablerSystem.isIdDisabledForRecipeOutput(norm);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                "[Lads SMP] Check '%s': GloballyDisabled=%s | LootDisabled=%s | RecipeDisabled=%s",
                norm, global, loot, recipe)).withStyle(global ? ChatFormatting.RED : ChatFormatting.GREEN), false);
        return global ? 1 : 0;
    }

    private static int executeDisable(CommandContext<CommandSourceStack> ctx, String rule, String reason) {
        boolean added = ItemDisablerSystem.addDisabledRule(rule, reason);
        int swept = 0;
        for (ServerPlayer player : ctx.getSource().getServer().getPlayerList().getPlayers()) {
            swept += ItemDisablerSystem.sanitizePlayerInventory(player, "admin_disable_" + rule);
        }
        int finalSwept = swept;
        if (added) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "[Lads SMP] Added disabled rule '" + rule + "' and sanitized " + finalSwept + " active inventory stack(s).")
                    .withStyle(ChatFormatting.GREEN), true);
            return 1;
        } else {
            ctx.getSource().sendFailure(Component.literal("[Lads SMP] Rule '" + rule + "' is already present."));
            return 0;
        }
    }

    private static int executeEnable(CommandContext<CommandSourceStack> ctx, String rule) {
        boolean removed = ItemDisablerSystem.removeDisabledRule(rule);
        if (removed) {
            ctx.getSource().sendSuccess(() -> Component.literal("[Lads SMP] Removed disabled rule '" + rule + "'.")
                    .withStyle(ChatFormatting.GREEN), true);
            return 1;
        } else {
            ctx.getSource().sendFailure(Component.literal("[Lads SMP] Rule '" + rule + "' was not found in disabledItems."));
            return 0;
        }
    }

    private static int executeDeathList(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        List<JsonObject> snaps = DeathBackupManager.getSnapshots(player.getStringUUID());
        if (snaps.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("[Lads SMP] No death backups recorded for " + player.getScoreboardName() + ".")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("=== Death Backups for " + player.getScoreboardName() + " (0 = latest) ===")
                .withStyle(ChatFormatting.GOLD), false);
        for (int i = 0; i < snaps.size(); i++) {
            JsonObject snap = snaps.get(snaps.size() - 1 - i);
            int idx = i;
            ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                    " [%d] %s | Cause: %s | Pos: %d, %d, %d (%s)",
                    idx,
                    snap.get("timestamp").getAsString(),
                    snap.get("cause").getAsString(),
                    snap.get("x").getAsInt(),
                    snap.get("y").getAsInt(),
                    snap.get("z").getAsInt(),
                    snap.get("dimension").getAsString())).withStyle(ChatFormatting.GRAY), false);
        }
        return snaps.size();
    }

    private static int executeDeathRestore(CommandContext<CommandSourceStack> ctx, ServerPlayer player, int index) {
        int count = DeathBackupManager.restoreSnapshot(player, index);
        if (count < 0) {
            ctx.getSource().sendFailure(Component.literal("[Lads SMP] Could not find death backup #" + index + " for " + player.getScoreboardName() + "."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[Lads SMP] Restored " + count + " item stack(s) from backup #" + index + " to " + player.getScoreboardName() + ".")
                .withStyle(ChatFormatting.GREEN), true);
        return count;
    }
}
