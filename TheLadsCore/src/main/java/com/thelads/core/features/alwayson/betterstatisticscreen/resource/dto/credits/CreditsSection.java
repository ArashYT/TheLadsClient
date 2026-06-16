/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.datafixers.util.Pair
 *  com.mojang.serialization.Codec
 *  com.mojang.serialization.DataResult
 *  com.mojang.serialization.DataResult$Error
 *  com.mojang.serialization.DynamicOps
 *  com.mojang.serialization.RecordBuilder
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.ComponentSerialization
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package com.thelads.core.features.alwayson.betterstatisticscreen.resource.dto.credits;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.RecordBuilder;
import com.thelads.core.features.alwayson.betterstatisticscreen.resource.dto.credits.CreditsEntry;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CreditsSection {
    public static final Codec<CreditsSection> CODEC = new CodecImpl();
    @NotNull
    private final Component name;
    @Nullable
    private final Component summary;
    @NotNull
    private final List<CreditsEntry> entries;

    public CreditsSection(@NotNull Component name, @Nullable Component summary, @NotNull List<CreditsEntry> entries) throws NullPointerException {
        this.name = Objects.requireNonNull(name);
        this.summary = summary;
        this.entries = Objects.requireNonNull(entries);
    }

    @NotNull
    public final Component getName() {
        return this.name;
    }

    @Nullable
    public final Component getSummary() {
        return this.summary;
    }

    @NotNull
    public final List<CreditsEntry> getEntries() {
        return this.entries;
    }

    private static final class CodecImpl
    implements Codec<CreditsSection> {
        private CodecImpl() {
        }

        @NotNull
        public final <T> DataResult<Pair<CreditsSection, T>> decode(@NotNull DynamicOps<T> ops, @NotNull T input) {
            try {
                return ops.getMap(input).flatMap(map -> {
                    DataResult<Component> name = ComponentSerialization.CODEC.parse(ops, map.get("name"));
                    Optional<Component> summary = ComponentSerialization.CODEC.parse(ops, map.get("summary")).result();
                    DataResult<List<CreditsEntry>> entries = CreditsEntry.CODEC.listOf().parse(ops, map.get("entries"));
                    if (name.error().isPresent()) {
                        return DataResult.error(() -> name.error().get().message());
                    }
                    if (entries.error().isPresent()) {
                        return DataResult.error(() -> entries.error().get().message());
                    }
                    return DataResult.success(Pair.of(new CreditsSection(name.getOrThrow(), summary.orElse(null), entries.getOrThrow()), input));
                });
            }
            catch (Exception e) {
                return DataResult.error(() -> String.valueOf(e.getClass()) + ": " + e.getMessage());
            }
        }

        @NotNull
        public final <T> DataResult<T> encode(@NotNull CreditsSection input, @NotNull DynamicOps<T> ops, @NotNull T prefix) {
            try {
                RecordBuilder<T> mapBuilder = ops.mapBuilder();
                mapBuilder.add("name", ComponentSerialization.CODEC.encodeStart(ops, input.getName()));
                if (input.getSummary() != null) {
                    mapBuilder.add("summary", ComponentSerialization.CODEC.encodeStart(ops, input.getSummary()));
                }
                mapBuilder.add("entries", CreditsEntry.CODEC.listOf().encodeStart(ops, input.getEntries()));
                return mapBuilder.build(prefix);
            }
            catch (Exception e) {
                return DataResult.error(() -> String.valueOf(e.getClass()) + ": " + e.getMessage());
            }
        }
    }
}

