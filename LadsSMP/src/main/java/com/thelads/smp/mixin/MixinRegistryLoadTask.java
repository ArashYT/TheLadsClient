package com.thelads.smp.mixin;

import com.google.gson.JsonElement;
import com.mojang.serialization.Decoder;
import com.thelads.smp.compat.LadsDataUpgrader;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Hooks RegistryLoadTask$PendingRegistration#loadFromResource at priority 500 (before MoogsStructureLib)
 * to upgrade legacy 1.20/1.21 loot conditions, 1,714 loot table 'functions' -> 'modifiers',
 * and 160 JustEnoughBreeding modded recipes to Minecraft 26.3, while stripping disabled content.
 */
@Mixin(targets = "net.minecraft.resources.RegistryLoadTask$PendingRegistration", priority = 500)
public abstract class MixinRegistryLoadTask {

    @ModifyVariable(
            method = "loadFromResource",
            at = @At(
                    value = "INVOKE_ASSIGN",
                    target = "Lnet/minecraft/util/StrictJsonParser;parse(Ljava/io/Reader;)Lcom/google/gson/JsonElement;"
            ),
            ordinal = 0
    )
    private static JsonElement ladssmp$upgradeAndFilterRegistryJson(
            JsonElement element,
            Decoder<?> decoder,
            RegistryOps<JsonElement> ops,
            ResourceKey<?> elementKey,
            Resource resource) {
        try {
            String registryPath = elementKey != null ? elementKey.registry().toString() : "";
            String entryId = elementKey != null ? elementKey.identifier().toString() : "";
            return LadsDataUpgrader.processRegistryJson(registryPath, entryId, element);
        } catch (Throwable ignored) {
            return element;
        }
    }
}
