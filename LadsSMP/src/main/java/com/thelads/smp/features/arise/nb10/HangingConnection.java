package com.thelads.smp.features.arise.nb10;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a persistent hanging connection between two attachment nodes.
 * Contains endpoint geometry, styling, color palette, and preserved drop item.
 */
public final class HangingConnection {

    private final UUID id;
    private final BlockPos targetPos;
    private final Direction targetFace;
    private final DecorationType type;
    private final int[] colors;
    private final float slack;
    private final ItemStack dropItem;

    public HangingConnection(
            UUID id,
            BlockPos targetPos,
            Direction targetFace,
            DecorationType type,
            int[] colors,
            float slack,
            ItemStack dropItem
    ) {
        this.id = id != null ? id : UUID.randomUUID();
        this.targetPos = Objects.requireNonNull(targetPos, "targetPos cannot be null");
        this.targetFace = targetFace != null ? targetFace : Direction.UP;
        this.type = type != null ? type : DecorationType.LIGHTS;
        this.colors = colors != null ? colors : new int[] { 0xFFE484 };
        this.slack = slack > 0.05F ? slack : 1.0F;
        this.dropItem = dropItem != null ? dropItem.copy() : null;
    }

    public UUID getId() {
        return id;
    }

    public BlockPos getTargetPos() {
        return targetPos;
    }

    public Direction getTargetFace() {
        return targetFace;
    }

    public DecorationType getType() {
        return type;
    }

    public int[] getColors() {
        return colors;
    }

    public float getSlack() {
        return slack;
    }

    public ItemStack getDropItem() {
        return dropItem != null ? dropItem.copy() : null;
    }

    /**
     * Serializes this connection into a 26.3 ValueOutput container.
     */
    public void save(ValueOutput out) {
        out.putString("id", id.toString());
        out.putInt("target_x", targetPos.getX());
        out.putInt("target_y", targetPos.getY());
        out.putInt("target_z", targetPos.getZ());
        out.putString("target_face", targetFace.getName());
        out.putString("type", type.getSerializedName());
        out.putIntArray("colors", colors);
        out.putFloat("slack", slack);
        if (dropItem != null && !dropItem.isEmpty()) {
            out.store("drop_item", ItemStack.CODEC, dropItem);
        }
    }

    /**
     * Deserializes a connection from a 26.3 ValueInput container.
     */
    public static HangingConnection load(ValueInput in) {
        String idStr = in.getStringOr("id", "");
        UUID uuid;
        try {
            uuid = idStr.isEmpty() ? UUID.randomUUID() : UUID.fromString(idStr);
        } catch (IllegalArgumentException e) {
            uuid = UUID.randomUUID();
        }

        int tx = in.getIntOr("target_x", 0);
        int ty = in.getIntOr("target_y", 0);
        int tz = in.getIntOr("target_z", 0);
        BlockPos target = new BlockPos(tx, ty, tz);

        String faceName = in.getStringOr("target_face", "up");
        Direction face = Direction.byName(faceName);
        if (face == null) face = Direction.UP;

        String typeName = in.getStringOr("type", "lights");
        DecorationType decoType = DecorationType.byName(typeName);

        int[] cols = in.getIntArray("colors").orElse(new int[] { 0xFFE484 });
        float slk = in.getFloatOr("slack", 1.0F);

        ItemStack item = in.read("drop_item", ItemStack.CODEC).orElse(null);

        return new HangingConnection(uuid, target, face, decoType, cols, slk, item);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HangingConnection that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "HangingConnection{" +
                "id=" + id +
                ", targetPos=" + targetPos +
                ", type=" + type +
                ", colors=" + Arrays.toString(colors) +
                ", slack=" + slack +
                '}';
    }
}
