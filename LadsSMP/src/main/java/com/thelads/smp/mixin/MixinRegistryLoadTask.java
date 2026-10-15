package com.thelads.smp.mixin;

import com.google.gson.JsonElement;
import com.thelads.smp.compat.LadsDataUpgrader;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Hooks RegistryLoadTask$PendingRegistration#loadFromResource at priority 500 (before MoogsStructureLib)
 * to upgrade legacy 1.20/1.21 loot conditions, 1,710 loot table 'functions' -> 'modifier',
 * and 157 JustEnoughBreeding modded recipes to Minecraft 26.3, while stripping disabled content.
 */
@Mixin(targets = "net.minecraft.resources.RegistryLoadTask$PendingRegistration", priority = 500)
public abstract class MixinRegistryLoadTask {
    @Shadow
    @Final
    ResourceKey<?> key;

    @ModifyVariable(
            method = "loadFromResource",
            at = @At(
                    value = "INVOKE_ASSIGN",
                    target = "Lnet/minecraft/util/StrictJsonParser;parse(Ljava/io/Reader;)Lcom/google/gson/JsonElement;"
            ),
            ordinal = 0
    )
    private JsonElement ladssmp$upgradeAndFilterRegistryJson(JsonElement element) {
        try {
            String registryPath = this.key != null ? this.key.registry().toString() : "";
            String entryId = this.key != null ? this.key.identifier().toString() : extractEntryId(this.toString());
            return LadsDataUpgrader.processRegistryJson(registryPath, entryId, element);
        } catch (Throwable ignored) {
            return element;
        }
    }

    private static String extractEntryId(String pendingRegString) {
        if (pendingRegString == null) return null;
        // ResourceKey.toString() inside PendingRegistration looks like "ResourceKey[minecraft:loot_table / ns:path]"
        int slashIdx = pendingRegString.indexOf(" / ");
        if (slashIdx != -1) {
            int closeBracket = pendingRegString.indexOf(']', slashIdx);
            if (closeBracket != -1) {
                return pendingRegString.substring(slashIdx + 3, closeBracket).trim();
            }
        }
        return null;
    }
}
