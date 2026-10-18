package com.thelads.smp.features.arise.nb8;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Shape calculation algorithms for Vein Mining (Shapeless, Small Tunnel, 3x3 Mining Tunnel).
 */
public enum VeinShape {
    SHAPELESS("Shapeless", "ladssmp.vein_mining.shape.shapeless"),
    SMALL_TUNNEL("Small Tunnel (1x2)", "ladssmp.vein_mining.shape.small_tunnel"),
    MINING_TUNNEL_3X3("3x3 Mining Tunnel", "ladssmp.vein_mining.shape.tunnel_3x3");

    private final String displayName;
    private final String translationKey;

    VeinShape(String displayName, String translationKey) {
        this.displayName = displayName;
        this.translationKey = translationKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    public VeinShape next() {
        VeinShape[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }

    public static VeinShape fromOrdinal(int ordinal) {
        VeinShape[] values = values();
        if (ordinal < 0 || ordinal >= values.length) {
            return SHAPELESS;
        }
        return values[ordinal];
    }

    /**
     * Compute the list of block positions to mine for this shape.
     *
     * @param level         the world level (can be null in tests)
     * @param startPos      origin block position
     * @param hitFace       targeted face of the block (or null)
     * @param playerFacing  horizontal facing direction of the player
     * @param isPlayerEyeLevel whether startPos is at or above player eye level
     * @param canMineFilter predicate determining if a position is valid for mining
     * @param maxBlocks     maximum blocks allowed
     * @return ordered list of block positions (including startPos if it passes filter)
     */
    public List<BlockPos> calculatePositions(
            Level level,
            BlockPos startPos,
            Direction hitFace,
            Direction playerFacing,
            boolean isPlayerEyeLevel,
            Predicate<BlockPos> canMineFilter,
            int maxBlocks) {

        if (maxBlocks <= 0 || startPos == null || canMineFilter == null) {
            return Collections.emptyList();
        }

        switch (this) {
            case SMALL_TUNNEL:
                return calculateSmallTunnel(startPos, hitFace, playerFacing, isPlayerEyeLevel, canMineFilter, maxBlocks);
            case MINING_TUNNEL_3X3:
                return calculate3x3Tunnel(startPos, hitFace, playerFacing, canMineFilter, maxBlocks);
            case SHAPELESS:
            default:
                return calculateShapeless(startPos, canMineFilter, maxBlocks);
        }
    }

    /**
     * Shapeless BFS flood-fill across 26-way adjacency.
     */
    private static List<BlockPos> calculateShapeless(BlockPos startPos, Predicate<BlockPos> canMineFilter, int maxBlocks) {
        List<BlockPos> result = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();

        if (canMineFilter.test(startPos)) {
            result.add(startPos);
        }
        visited.add(startPos);
        queue.add(startPos);

        while (!queue.isEmpty() && result.size() < maxBlocks) {
            BlockPos current = queue.poll();

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;

                        BlockPos neighbor = current.offset(dx, dy, dz);
                        if (visited.add(neighbor)) {
                            // Max distance safeguard (radius 24 from startPos)
                            if (Math.abs(neighbor.getX() - startPos.getX()) > 24
                                    || Math.abs(neighbor.getY() - startPos.getY()) > 24
                                    || Math.abs(neighbor.getZ() - startPos.getZ()) > 24) {
                                continue;
                            }

                            if (canMineFilter.test(neighbor)) {
                                result.add(neighbor);
                                queue.add(neighbor);
                                if (result.size() >= maxBlocks) {
                                    return result;
                                }
                            }
                        }
                    }
                }
            }
        }

        return result;
    }

    /**
     * Small Tunnel: 1 block wide x 2 blocks high walking corridor.
     */
    private static List<BlockPos> calculateSmallTunnel(
            BlockPos startPos,
            Direction hitFace,
            Direction playerFacing,
            boolean isPlayerEyeLevel,
            Predicate<BlockPos> canMineFilter,
            int maxBlocks) {

        List<BlockPos> result = new ArrayList<>();
        Direction digDir = hitFace != null && hitFace.getAxis().isHorizontal()
                ? hitFace.getOpposite()
                : (playerFacing != null ? playerFacing : Direction.NORTH);

        if (hitFace == Direction.UP) {
            digDir = Direction.DOWN;
        } else if (hitFace == Direction.DOWN) {
            digDir = Direction.UP;
        }

        int maxDepth = Math.max(1, maxBlocks / 2);

        if (digDir.getAxis() == Direction.Axis.Y) {
            // Vertical shaft
            for (int d = 0; d < maxBlocks; d++) {
                BlockPos p = startPos.relative(digDir, d);
                if (canMineFilter.test(p)) {
                    result.add(p);
                    if (result.size() >= maxBlocks) break;
                }
            }
            return result;
        }

        // Horizontal 1x2 tunnel
        int secondBlockYOffset = isPlayerEyeLevel ? -1 : 1;

        for (int d = 0; d < maxDepth; d++) {
            BlockPos p1 = startPos.relative(digDir, d);
            BlockPos p2 = p1.offset(0, secondBlockYOffset, 0);

            if (canMineFilter.test(p1)) {
                result.add(p1);
                if (result.size() >= maxBlocks) break;
            }
            if (canMineFilter.test(p2)) {
                result.add(p2);
                if (result.size() >= maxBlocks) break;
            }
        }

        return result;
    }

    /**
     * 3x3 Mining Tunnel: centered 3x3 plane advancing perpendicular into the surface.
     */
    private static List<BlockPos> calculate3x3Tunnel(
            BlockPos startPos,
            Direction hitFace,
            Direction playerFacing,
            Predicate<BlockPos> canMineFilter,
            int maxBlocks) {

        List<BlockPos> result = new ArrayList<>();
        Direction digDir = hitFace != null ? hitFace.getOpposite() : (playerFacing != null ? playerFacing : Direction.NORTH);
        int maxDepth = Math.max(1, maxBlocks / 9);

        Direction.Axis axis = digDir.getAxis();

        for (int depth = 0; depth < maxDepth; depth++) {
            BlockPos centerLayer = startPos.relative(digDir, depth);

            for (int u = -1; u <= 1; u++) {
                for (int v = -1; v <= 1; v++) {
                    BlockPos p;
                    if (axis == Direction.Axis.Y) {
                        // Horizontal plane, depth along Y
                        p = centerLayer.offset(u, 0, v);
                    } else if (axis == Direction.Axis.X) {
                        // YZ plane, depth along X
                        p = centerLayer.offset(0, u, v);
                    } else {
                        // XY plane, depth along Z
                        p = centerLayer.offset(u, v, 0);
                    }

                    if (canMineFilter.test(p)) {
                        result.add(p);
                        if (result.size() >= maxBlocks) {
                            return result;
                        }
                    }
                }
            }
        }

        return result;
    }
}
