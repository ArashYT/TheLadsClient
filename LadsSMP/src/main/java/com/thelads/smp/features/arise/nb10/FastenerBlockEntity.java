package com.thelads.smp.features.arise.nb10;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Block entity for decoration fasteners.
 * Stores and synchronizes outgoing hanging connections, manages dropped items on destruction,
 * and maintains client-side cached geometry for stutter-free rendering.
 */
public class FastenerBlockEntity extends BlockEntity {

    private final List<HangingConnection> connections = new CopyOnWriteArrayList<>();
    private Object clientCachedMesh = null; // Stored as Object to remain server-safe

    public FastenerBlockEntity(BlockPos pos, BlockState state) {
        super(AriseNB10Features.FASTENER_BLOCK_ENTITY_TYPE, pos, state);
    }

    public List<HangingConnection> getConnections() {
        return Collections.unmodifiableList(connections);
    }

    public boolean hasConnections() {
        return !connections.isEmpty();
    }

    public boolean hasLightConnections() {
        for (HangingConnection conn : connections) {
            if (conn.getType() == DecorationType.LIGHTS) {
                return true;
            }
        }
        return false;
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

    public boolean removeConnection(UUID id) {
        for (HangingConnection conn : connections) {
            if (conn.getId().equals(id)) {
                connections.remove(conn);
                clientCachedMesh = null;
                if (level != null && !level.isClientSide()) {
                    ItemStack drop = conn.getDropItem();
                    if (!drop.isEmpty() && AriseNB10Config.get().dropOnBreak) {
                        Containers.dropItemStack(level,
                                worldPosition.getX() + 0.5,
                                worldPosition.getY() + 0.5,
                                worldPosition.getZ() + 0.5,
                                drop);
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

    public void dropAllAndClean() {
        if (level != null && !level.isClientSide()) {
            for (HangingConnection conn : connections) {
                ItemStack drop = conn.getDropItem();
                if (!drop.isEmpty() && AriseNB10Config.get().dropOnBreak) {
                    Containers.dropItemStack(level,
                            worldPosition.getX() + 0.5,
                            worldPosition.getY() + 0.5,
                            worldPosition.getZ() + 0.5,
                            drop);
                }
            }
        }
        connections.clear();
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
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        connections.clear();
        clientCachedMesh = null;
        int count = in.getIntOr("conn_count", 0);
        for (int i = 0; i < count; i++) {
            in.child("conn_" + i).ifPresent(child -> {
                connections.add(HangingConnection.load(child));
            });
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
