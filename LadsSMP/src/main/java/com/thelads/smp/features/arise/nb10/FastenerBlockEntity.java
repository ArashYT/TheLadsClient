package com.thelads.smp.features.arise.nb10;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Block entity for decoration fasteners.
 * Stores and synchronizes both outgoing and incoming hanging connections bidirectionally,
 * guarantees zero item voiding on break or detach at either endpoint,
 * and maintains client-side cached geometry for stutter-free rendering.
 */
public class FastenerBlockEntity extends BlockEntity {

    public record IncomingLink(UUID id, BlockPos sourcePos) {}

    private final List<HangingConnection> connections = new CopyOnWriteArrayList<>();
    private final List<IncomingLink> incomingConnections = new CopyOnWriteArrayList<>();
    private Object clientCachedMesh = null; // Stored as Object to remain server-safe
    private boolean brokenByCreative = false;

    public FastenerBlockEntity(BlockPos pos, BlockState state) {
        super(AriseNB10Features.FASTENER_BLOCK_ENTITY_TYPE, pos, state);
    }

    public List<HangingConnection> getConnections() {
        return Collections.unmodifiableList(connections);
    }

    public List<IncomingLink> getIncomingConnections() {
        return Collections.unmodifiableList(incomingConnections);
    }

    public boolean hasConnections() {
        return !connections.isEmpty() || !incomingConnections.isEmpty();
    }

    public boolean hasLightConnections() {
        for (HangingConnection conn : connections) {
            if (conn.getType() == DecorationType.LIGHTS) {
                return true;
            }
        }
        return false;
    }

    public void setBrokenByCreative(boolean brokenByCreative) {
        this.brokenByCreative = brokenByCreative;
    }

    public void addConnection(HangingConnection connection) {
        connections.add(connection);
        clientCachedMesh = null;
        updateBlockLightState();
        setChanged();
        if (level instanceof ServerLevel sl) {
            sl.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    public void addIncomingConnection(UUID id, BlockPos sourcePos) {
        incomingConnections.add(new IncomingLink(id, sourcePos));
        setChanged();
        if (level instanceof ServerLevel sl) {
            sl.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    public void removeIncomingLink(UUID id) {
        incomingConnections.removeIf(link -> link.id().equals(id));
        setChanged();
    }

    public boolean removeConnection(UUID id) {
        return removeConnection(id, null);
    }

    public boolean removeConnection(UUID id, @Nullable BlockPos dropAt) {
        for (HangingConnection conn : connections) {
            if (conn.getId().equals(id)) {
                connections.remove(conn);
                clientCachedMesh = null;

                if (level != null && !level.isClientSide()) {
                    if (!brokenByCreative && AriseNB10Config.get().dropOnBreak) {
                        ItemStack drop = conn.getDropItem();
                        if (drop != null && !drop.isEmpty()) {
                            BlockPos targetDrop = dropAt != null ? dropAt : worldPosition;
                            Containers.dropItemStack(level,
                                    targetDrop.getX() + 0.5,
                                    targetDrop.getY() + 0.5,
                                    targetDrop.getZ() + 0.5,
                                    drop);
                        }
                    }

                    // Clean up incoming link on the destination fastener
                    if (level.isLoaded(conn.getTargetPos())) {
                        BlockEntity be = level.getBlockEntity(conn.getTargetPos());
                        if (be instanceof FastenerBlockEntity targetBe) {
                            targetBe.removeIncomingLink(id);
                        }
                    }
                }

                updateBlockLightState();
                setChanged();
                if (level instanceof ServerLevel sl) {
                    sl.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Detaches one connection from this fastener (from either outgoing or incoming connections).
     * Guaranteed to drop the item at this fastener's position so the player receives it.
     */
    public boolean detachOne(@Nullable Player player) {
        boolean isCreative = player != null && player.isCreative();
        if (isCreative) {
            brokenByCreative = true;
        }

        try {
            if (!connections.isEmpty()) {
                HangingConnection last = connections.get(connections.size() - 1);
                return removeConnection(last.getId(), worldPosition);
            } else if (!incomingConnections.isEmpty()) {
                IncomingLink last = incomingConnections.get(incomingConnections.size() - 1);
                incomingConnections.remove(last);
                setChanged();

                if (level != null && level.isLoaded(last.sourcePos())) {
                    BlockEntity be = level.getBlockEntity(last.sourcePos());
                    if (be instanceof FastenerBlockEntity sourceBe) {
                        return sourceBe.removeConnection(last.id(), worldPosition);
                    }
                }
                return true;
            }
            return false;
        } finally {
            if (isCreative) {
                brokenByCreative = false;
            }
        }
    }

    public void dropAllAndClean() {
        if (level != null && !level.isClientSide()) {
            // 1. Clean and drop all outgoing connections
            for (HangingConnection conn : connections) {
                if (!brokenByCreative && AriseNB10Config.get().dropOnBreak) {
                    ItemStack drop = conn.getDropItem();
                    if (drop != null && !drop.isEmpty()) {
                        Containers.dropItemStack(level,
                                worldPosition.getX() + 0.5,
                                worldPosition.getY() + 0.5,
                                worldPosition.getZ() + 0.5,
                                drop);
                    }
                }
                // Notify target endpoint
                if (level.isLoaded(conn.getTargetPos())) {
                    BlockEntity be = level.getBlockEntity(conn.getTargetPos());
                    if (be instanceof FastenerBlockEntity targetBe) {
                        targetBe.removeIncomingLink(conn.getId());
                    }
                }
            }

            // 2. Notify all source endpoints for incoming connections and drop items here
            for (IncomingLink link : incomingConnections) {
                if (level.isLoaded(link.sourcePos())) {
                    BlockEntity be = level.getBlockEntity(link.sourcePos());
                    if (be instanceof FastenerBlockEntity sourceBe) {
                        sourceBe.removeConnection(link.id(), worldPosition);
                    }
                }
            }
        }

        connections.clear();
        incomingConnections.clear();
        clientCachedMesh = null;
        updateBlockLightState();
        setChanged();
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        dropAllAndClean();
        super.preRemoveSideEffects(pos, state);
    }

    public void updateBlockLightState() {
        if (level == null || level.isClientSide()) return;
        BlockState current = getBlockState();
        if (current.is(AriseNB10Features.FASTENER_BLOCK)) {
            boolean shouldHaveLights = hasLightConnections();
            if (current.getValue(FastenerBlock.HAS_LIGHTS) != shouldHaveLights) {
                level.setBlock(worldPosition, current.setValue(FastenerBlock.HAS_LIGHTS, shouldHaveLights), Block.UPDATE_ALL);
            }
        }
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("conn_count", connections.size());
        for (int i = 0; i < connections.size(); i++) {
            ValueOutput child = out.child("conn_" + i);
            connections.get(i).save(child);
        }

        out.putInt("inc_count", incomingConnections.size());
        for (int i = 0; i < incomingConnections.size(); i++) {
            IncomingLink link = incomingConnections.get(i);
            out.putString("inc_id_" + i, link.id().toString());
            out.putInt("inc_x_" + i, link.sourcePos().getX());
            out.putInt("inc_y_" + i, link.sourcePos().getY());
            out.putInt("inc_z_" + i, link.sourcePos().getZ());
        }
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        connections.clear();
        incomingConnections.clear();
        clientCachedMesh = null;

        int count = in.getIntOr("conn_count", 0);
        for (int i = 0; i < count; i++) {
            in.child("conn_" + i).ifPresent(child -> {
                connections.add(HangingConnection.load(child));
            });
        }

        int incCount = in.getIntOr("inc_count", 0);
        for (int i = 0; i < incCount; i++) {
            String idStr = in.getStringOr("inc_id_" + i, "");
            UUID id;
            try {
                id = idStr.isEmpty() ? UUID.randomUUID() : UUID.fromString(idStr);
            } catch (IllegalArgumentException e) {
                id = UUID.randomUUID();
            }
            int x = in.getIntOr("inc_x_" + i, 0);
            int y = in.getIntOr("inc_y_" + i, 0);
            int z = in.getIntOr("inc_z_" + i, 0);
            incomingConnections.add(new IncomingLink(id, new BlockPos(x, y, z)));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider lookup) {
        return saveCustomOnly(lookup);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public Object getClientCachedMesh() {
        return clientCachedMesh;
    }

    public void setClientCachedMesh(Object clientCachedMesh) {
        this.clientCachedMesh = clientCachedMesh;
    }

    public boolean shouldRenderAtDistance(Vec3 cameraPos, double maxDistance) {
        double maxDistSq = maxDistance * maxDistance;
        double dx = (worldPosition.getX() + 0.5) - cameraPos.x;
        double dy = (worldPosition.getY() + 0.5) - cameraPos.y;
        double dz = (worldPosition.getZ() + 0.5) - cameraPos.z;
        double distSq = dx * dx + dy * dy + dz * dz;

        if (distSq <= maxDistSq) {
            return true;
        }

        // Also check if any target endpoint is within distance
        for (HangingConnection conn : connections) {
            BlockPos tp = conn.getTargetPos();
            double tdx = (tp.getX() + 0.5) - cameraPos.x;
            double tdy = (tp.getY() + 0.5) - cameraPos.y;
            double tdz = (tp.getZ() + 0.5) - cameraPos.z;
            if (tdx * tdx + tdy * tdy + tdz * tdz <= maxDistSq) {
                return true;
            }
        }
        return false;
    }
}
