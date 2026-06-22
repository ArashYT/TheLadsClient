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

public record SaturationSyncPayload(float saturation) implements CustomPacketPayload
{
    public static final StreamCodec<FriendlyByteBuf, SaturationSyncPayload> CODEC = CustomPacketPayload.codec(SaturationSyncPayload::write, SaturationSyncPayload::new);
    public static final CustomPacketPayload.Type<SaturationSyncPayload> ID = new CustomPacketPayload.Type(Identifier.fromNamespaceAndPath("appleskin", "saturation"));

    public SaturationSyncPayload(FriendlyByteBuf buf) {
        this(buf.readFloat());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeFloat(this.saturation);
    }

    public float getSaturation() {
        return this.saturation;
    }

    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}

