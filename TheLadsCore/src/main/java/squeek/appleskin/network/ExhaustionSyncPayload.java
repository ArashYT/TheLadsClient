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

public record ExhaustionSyncPayload(float exhaustion) implements CustomPacketPayload
{
    public static final StreamCodec<FriendlyByteBuf, ExhaustionSyncPayload> CODEC = CustomPacketPayload.codec(ExhaustionSyncPayload::write, ExhaustionSyncPayload::new);
    public static final CustomPacketPayload.Type<ExhaustionSyncPayload> ID = new CustomPacketPayload.Type(Identifier.fromNamespaceAndPath("appleskin", "exhaustion"));

    public ExhaustionSyncPayload(FriendlyByteBuf buf) {
        this(buf.readFloat());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeFloat(this.exhaustion);
    }

    public float getExhaustion() {
        return this.exhaustion;
    }

    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}

