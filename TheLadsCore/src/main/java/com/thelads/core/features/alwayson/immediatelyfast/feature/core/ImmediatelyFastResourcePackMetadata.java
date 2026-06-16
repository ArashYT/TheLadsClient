/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.datafixers.kinds.App
 *  com.mojang.datafixers.kinds.Applicative
 *  com.mojang.serialization.Codec
 *  com.mojang.serialization.codecs.RecordCodecBuilder
 *  net.minecraft.server.packs.metadata.MetadataSectionType
 */
package com.thelads.core.features.alwayson.immediatelyfast.feature.core;

import com.mojang.datafixers.kinds.App;
import com.mojang.datafixers.kinds.Applicative;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collections;
import java.util.List;
import net.minecraft.server.packs.metadata.MetadataSectionType;

public record ImmediatelyFastResourcePackMetadata(List<String> compatibleFeatures) {
    public static final ImmediatelyFastResourcePackMetadata DEFAULT = new ImmediatelyFastResourcePackMetadata(Collections.emptyList());
    public static final Codec<ImmediatelyFastResourcePackMetadata> CODEC = RecordCodecBuilder.create(instance -> instance.group(Codec.STRING.listOf().fieldOf("compatible_features").forGetter(ImmediatelyFastResourcePackMetadata::compatibleFeatures)).apply(instance, ImmediatelyFastResourcePackMetadata::new));
    public static final MetadataSectionType<ImmediatelyFastResourcePackMetadata> SERIALIZER = new MetadataSectionType("immediatelyfast", CODEC);
}

