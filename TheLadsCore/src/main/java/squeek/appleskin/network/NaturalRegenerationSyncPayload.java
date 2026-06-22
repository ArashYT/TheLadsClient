/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.network.FriendlyByteBuf
 *  net.minecraft.network.codec.StreamCodec
 *  net.minecraft.network.protocol.common.custom.CustomPacketPayload
 *  net.minecraft.network.protocol.common.custom.CustomPacketPayload$Type
 */
package squeek.appleskin.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record NaturalRegenerationSyncPayload(boolean naturalRegeneration) implements CustomPacketPayload
{
    public static final StreamCodec<FriendlyByteBuf, NaturalRegenerationSyncPayload> CODEC = CustomPacketPayload.codec(NaturalRegenerationSyncPayload::write, NaturalRegenerationSyncPayload::new);
    public static final CustomPacketPayload.Type<NaturalRegenerationSyncPayload> ID = new CustomPacketPayload.Type(Identifier.fromNamespaceAndPath("appleskin", "natural_regeneration"));

    public NaturalRegenerationSyncPayload(FriendlyByteBuf buf) {
        this(buf.readBoolean());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(this.naturalRegeneration);
    }

    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}

