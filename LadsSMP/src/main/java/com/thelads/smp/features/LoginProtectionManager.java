package com.thelads.smp.features;

import com.thelads.smp.config.LadsConfig;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Consolidates 'logprot': Players receive temporary invulnerability for N seconds
 * upon login, dimension travel, or respawning, which ends early if the player moves or acts.
 */
public final class LoginProtectionManager {
    private static final Map<UUID, Long> PROTECTED_PLAYERS = new ConcurrentHashMap<>();

    private LoginProtectionManager() {}

    public static void init() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (handler.player != null) {
                protect(handler.player);
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (handler.player != null) {
                removeProtection(handler.player);
            }
        });

        AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                removeProtection(serverPlayer);
            }
            return InteractionResult.PASS;
        });

        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                removeProtection(serverPlayer);
            }
            return InteractionResult.PASS;
        });

        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                removeProtection(serverPlayer);
            }
            return InteractionResult.PASS;
        });
    }

    public static void protect(ServerPlayer player) {
        if (!LadsConfig.get().features.loginProtection) return;
        long expiry = System.currentTimeMillis() + (long) LadsConfig.get().features.loginProtectionSeconds * 1000L;
        PROTECTED_PLAYERS.put(player.getUUID(), expiry);
    }

    public static void removeProtection(ServerPlayer player) {
        PROTECTED_PLAYERS.remove(player.getUUID());
    }

    public static boolean hasProtection(ServerPlayer player) {
        if (!LadsConfig.get().features.loginProtection) return false;
        Long until = PROTECTED_PLAYERS.get(player.getUUID());
        if (until == null) return false;
        if (System.currentTimeMillis() > until) {
            PROTECTED_PLAYERS.remove(player.getUUID());
            return false;
        }
        return true;
    }

    public static boolean isProtected(ServerPlayer player, DamageSource source) {
        if (!hasProtection(player)) return false;
        if (source.is(DamageTypes.FELL_OUT_OF_WORLD)
                || source.is(DamageTypes.GENERIC_KILL)
                || source.is(DamageTypes.FALL)) {
            return false;
        }
        return true;
    }
}
