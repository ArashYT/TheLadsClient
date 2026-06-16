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
import com.thelads.core.features.alwayson.betterstatisticscreen.resource.BCodecs;
import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CreditsEntry {
    public static final Codec<CreditsEntry> CODEC = new CodecImpl();
    @Nullable
    private final URI avatar_uri;
    @NotNull
    private final Component name;
    @Nullable
    private final Component summary;
    @Nullable
    private final URI homepage_uri;
    private final int _hashCode;

    public CreditsEntry(@Nullable URI avatar_uri, @NotNull Component name, @Nullable Component summary, @Nullable URI homepage_uri) throws NullPointerException {
        this.avatar_uri = avatar_uri;
        this.name = Objects.requireNonNull(name);
        this.summary = summary;
        this.homepage_uri = homepage_uri;
        this._hashCode = Objects.hash(avatar_uri, name, summary, homepage_uri);
    }

    public int hashCode() {
        return this._hashCode;
    }

    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || this.getClass() != obj.getClass()) {
            return false;
        }
        CreditsEntry other = (CreditsEntry)obj;
        return Objects.equals(this.avatar_uri, other.avatar_uri) && this.name.equals((Object)other.name) && Objects.equals(this.summary, other.summary) && Objects.equals(this.homepage_uri, other.homepage_uri);
    }

    @Nullable
    public final URI getAvatarURI() {
        return this.avatar_uri;
    }

    @NotNull
    public final Component getName() {
        return this.name;
    }

    @Nullable
    public final Component getSummary() {
        return this.summary;
    }

    @Nullable
    public final URI getHomepageURI() {
        return this.homepage_uri;
    }

    private static final class CodecImpl
    implements Codec<CreditsEntry> {
        private CodecImpl() {
        }

        @NotNull
        public final <T> DataResult<Pair<CreditsEntry, T>> decode(@NotNull DynamicOps<T> ops, @NotNull T input) {
            try {
                return ops.getMap(input).flatMap(map -> {
                    Optional<URI> avatar_uri = BCodecs.URI.parse(ops, map.get("avatar_uri")).result();
                    DataResult<Component> name = ComponentSerialization.CODEC.parse(ops, map.get("name"));
                    Optional<URI> homepage_uri = BCodecs.URI.parse(ops, map.get("homepage_uri")).result();
                    Optional<Component> summary = ComponentSerialization.CODEC.parse(ops, map.get("summary")).result();
                    if (name.error().isPresent()) {
                        return DataResult.error(() -> name.error().get().message());
                    }
                    return DataResult.success(Pair.of(new CreditsEntry(avatar_uri.orElse(null), name.getOrThrow(), summary.orElse(null), homepage_uri.orElse(null)), input));
                });
            }
            catch (Exception e) {
                return DataResult.error(() -> String.valueOf(e.getClass()) + ": " + e.getMessage());
            }
        }

        @NotNull
        public final <T> DataResult<T> encode(@NotNull CreditsEntry input, @NotNull DynamicOps<T> ops, @NotNull T prefix) {
            try {
                RecordBuilder<T> mapBuilder = ops.mapBuilder();
                if (input.getAvatarURI() != null) {
                    mapBuilder.add("avatar_uri", BCodecs.URI.encodeStart(ops, input.getAvatarURI()));
                }
                mapBuilder.add("name", ComponentSerialization.CODEC.encodeStart(ops, input.getName()));
                if (input.getSummary() != null) {
                    mapBuilder.add("summary", ComponentSerialization.CODEC.encodeStart(ops, input.getSummary()));
                }
                if (input.getHomepageURI() != null) {
                    mapBuilder.add("homepage_uri", BCodecs.URI.encodeStart(ops, input.getHomepageURI()));
                }
                return mapBuilder.build(prefix);
            }
            catch (Exception e) {
                return DataResult.error(() -> String.valueOf(e.getClass()) + ": " + e.getMessage());
            }
        }
    }
}

