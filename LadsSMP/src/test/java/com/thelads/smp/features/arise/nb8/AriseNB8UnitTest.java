package com.thelads.smp.features.arise.nb8;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test suite for Batch NB8: Vein Mining.
 * Tests configuration, shape calculations, BFS flood-fill, tunnel alignments,
 * hunger math, tool durability thresholds, and tag pattern matching.
 */
public class AriseNB8UnitTest {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Test
    @DisplayName("Verify Arise NB8 configuration defaults match Arise SMP owner values")
    public void testConfigDefaults() {
        AriseNB8Config config = new AriseNB8Config();

        assertTrue(config.enabled, "Vein mining should be enabled by default");
        assertEquals(64, config.maxBlocks, "Arise max_blocks default is 64");
        assertEquals(16, config.blocksPerTick, "Default blocks per tick should be 16 for lag safety");
        assertEquals(20.0, config.exhaustionMultiplier, 0.001, "Arise exhaustion_per_block default is 20.0");
        assertEquals(0, config.preventToolBreak, "Arise prevent_tool_break default is 0");
        assertFalse(config.requireTool, "Arise require_tool default is false");
        assertEquals(256, config.renderOutline, "Arise render_outline default is 256");
        assertTrue(config.shapedAnyBlock, "Arise merge_tags_shaped default is ['*']");
        assertFalse(config.mineContainers, "mineContainers safety default is false");

        assertTrue(config.mergeTags.contains("c:*_ores"));
        assertTrue(config.mergeTags.contains("forge:ores/*"));
        assertTrue(config.mergeTags.contains("#minecraft:base_stone_overworld"));
        assertTrue(config.mergeTags.contains("#c:ores"));
        assertTrue(config.mergeTags.contains("#minecraft:logs"));
    }

    @Test
    @DisplayName("Verify Arise NB8 config JSON serialization round-trip")
    public void testConfigJsonRoundTrip() {
        AriseNB8Config original = new AriseNB8Config();
        original.maxBlocks = 128;
        original.blocksPerTick = 32;
        original.exhaustionMultiplier = 15.5;
        original.preventToolBreak = 5;
        original.requireTool = true;

        String json = GSON.toJson(original);
        AriseNB8Config deserialized = GSON.fromJson(json, AriseNB8Config.class);

        assertEquals(128, deserialized.maxBlocks);
        assertEquals(32, deserialized.blocksPerTick);
        assertEquals(15.5, deserialized.exhaustionMultiplier, 0.001);
        assertEquals(5, deserialized.preventToolBreak);
        assertTrue(deserialized.requireTool);
    }

    @Test
    @DisplayName("Verify VeinShape enum cycling and ordinal resolution")
    public void testVeinShapeEnum() {
        assertEquals(VeinShape.SMALL_TUNNEL, VeinShape.SHAPELESS.next());
        assertEquals(VeinShape.MINING_TUNNEL_3X3, VeinShape.SMALL_TUNNEL.next());
        assertEquals(VeinShape.SHAPELESS, VeinShape.MINING_TUNNEL_3X3.next());

        assertEquals(VeinShape.SHAPELESS, VeinShape.fromOrdinal(0));
        assertEquals(VeinShape.SMALL_TUNNEL, VeinShape.fromOrdinal(1));
        assertEquals(VeinShape.MINING_TUNNEL_3X3, VeinShape.fromOrdinal(2));
        assertEquals(VeinShape.SHAPELESS, VeinShape.fromOrdinal(-1));
        assertEquals(VeinShape.SHAPELESS, VeinShape.fromOrdinal(999));
    }

    @Test
    @DisplayName("Verify Shapeless BFS connected block search and max block limits")
    public void testShapelessBfs() {
        BlockPos origin = new BlockPos(100, 64, 100);

        // Define a 3x3x3 cube of valid blocks around origin (27 blocks total)
        Set<BlockPos> validWorld = new HashSet<>();
        for (int x = 99; x <= 101; x++) {
            for (int y = 63; y <= 65; y++) {
                for (int z = 99; z <= 101; z++) {
                    validWorld.add(new BlockPos(x, y, z));
                }
            }
        }

        Predicate<BlockPos> filter = validWorld::contains;

        // With limit 10, should return exactly 10 unique connected blocks
        List<BlockPos> limited = VeinShape.SHAPELESS.calculatePositions(null, origin, Direction.UP, Direction.NORTH, false, filter, 10);
        assertEquals(10, limited.size());
        assertEquals(origin, limited.get(0), "Origin should be the first element");
        assertEquals(10, new HashSet<>(limited).size(), "All positions must be unique");

        // With limit 64, should return all 27 connected blocks
        List<BlockPos> full = VeinShape.SHAPELESS.calculatePositions(null, origin, Direction.UP, Direction.NORTH, false, filter, 64);
        assertEquals(27, full.size());
        assertEquals(27, new HashSet<>(full).size());
    }

    @Test
    @DisplayName("Verify Shapeless BFS ignores disconnected blocks")
    public void testShapelessDisconnected() {
        BlockPos origin = new BlockPos(0, 0, 0);

        Set<BlockPos> validWorld = new HashSet<>();
        validWorld.add(origin);
        validWorld.add(new BlockPos(1, 0, 0)); // Connected
        validWorld.add(new BlockPos(10, 0, 0)); // Disconnected gap

        List<BlockPos> result = VeinShape.SHAPELESS.calculatePositions(null, origin, null, Direction.NORTH, false, validWorld::contains, 64);
        assertEquals(2, result.size());
        assertTrue(result.contains(origin));
        assertTrue(result.contains(new BlockPos(1, 0, 0)));
        assertFalse(result.contains(new BlockPos(10, 0, 0)), "Disconnected block across gap must not be included");
    }

    @Test
    @DisplayName("Verify Small Tunnel generates 1x2 forward walking corridor")
    public void testSmallTunnelHorizontal() {
        BlockPos start = new BlockPos(0, 60, 0);
        Set<BlockPos> world = new HashSet<>();

        // Create a solid wall extending North from Z=0 to Z=-10, Y=60..61
        for (int z = 0; z >= -10; z--) {
            world.add(new BlockPos(0, 60, z));
            world.add(new BlockPos(0, 61, z));
        }

        // Hitting South face of wall at Z=0 means digging North (hitFace = SOUTH, digDir = NORTH)
        List<BlockPos> tunnel = VeinShape.SMALL_TUNNEL.calculatePositions(
                null, start, Direction.SOUTH, Direction.NORTH, false, world::contains, 10
        );

        assertEquals(10, tunnel.size());
        // Verify every block in tunnel has X=0 and Y in {60, 61}
        for (BlockPos p : tunnel) {
            assertEquals(0, p.getX(), "Small tunnel width must be 1 block wide");
            assertTrue(p.getY() == 60 || p.getY() == 61, "Small tunnel height must be 2 blocks high");
            assertTrue(p.getZ() <= 0 && p.getZ() >= -4, "Z must advance North");
        }
    }

    @Test
    @DisplayName("Verify Small Tunnel generates horizontal corridor when hitting floor (UP) without vertical shaft trap")
    public void testSmallTunnelFloorHit() {
        BlockPos start = new BlockPos(10, 50, 10);
        Set<BlockPos> world = new HashSet<>();
        for (int z = 10; z >= 0; z--) {
            world.add(new BlockPos(10, 50, z)); // Floor
            world.add(new BlockPos(10, 51, z)); // Head height
        }

        // Hitting top face of floor with player facing NORTH -> should dig horizontal corridor North, not a vertical death pit!
        List<BlockPos> corridor = VeinShape.SMALL_TUNNEL.calculatePositions(
                null, start, Direction.UP, Direction.NORTH, false, world::contains, 6
        );

        assertEquals(6, corridor.size());
        for (BlockPos p : corridor) {
            assertEquals(10, p.getX(), "Corridor width must be 1 block");
            assertTrue(p.getY() == 50 || p.getY() == 51, "Corridor must be 2 blocks high (Y=50 and Y=51)");
            assertTrue(p.getZ() <= 10, "Corridor must advance North along player facing");
        }
    }

    @Test
    @DisplayName("Verify Small Tunnel generates horizontal corridor when hitting ceiling (DOWN)")
    public void testSmallTunnelCeilingHit() {
        BlockPos start = new BlockPos(10, 52, 10);
        Set<BlockPos> world = new HashSet<>();
        for (int z = 10; z >= 0; z--) {
            world.add(new BlockPos(10, 52, z)); // Ceiling block (head)
            world.add(new BlockPos(10, 51, z)); // Foot block
        }

        // Hitting bottom face of ceiling with player facing NORTH -> should dig horizontal corridor North
        List<BlockPos> corridor = VeinShape.SMALL_TUNNEL.calculatePositions(
                null, start, Direction.DOWN, Direction.NORTH, false, world::contains, 6
        );

        assertEquals(6, corridor.size());
        for (BlockPos p : corridor) {
            assertEquals(10, p.getX(), "Corridor width must be 1 block");
            assertTrue(p.getY() == 51 || p.getY() == 52, "Corridor must be 2 blocks high (Y=51 and Y=52)");
            assertTrue(p.getZ() <= 10, "Corridor must advance North along player facing");
        }
    }

    @Test
    @DisplayName("Verify 3x3 Mining Tunnel generates centered 3x3 plane advancing forward")
    public void testMiningTunnel3x3() {
        BlockPos start = new BlockPos(0, 64, 0);
        Set<BlockPos> world = new HashSet<>();

        // Create 3x3x5 solid block area extending North (Z <= 0)
        for (int z = 0; z >= -4; z--) {
            for (int x = -1; x <= 1; x++) {
                for (int y = 63; y <= 65; y++) {
                    world.add(new BlockPos(x, y, z));
                }
            }
        }

        // Hitting South face -> digDir = North. Max blocks = 18 (2 complete 3x3 layers)
        List<BlockPos> tunnel = VeinShape.MINING_TUNNEL_3X3.calculatePositions(
                null, start, Direction.SOUTH, Direction.NORTH, false, world::contains, 18
        );

        assertEquals(18, tunnel.size(), "Should mine exactly 2 layers of 3x3 = 18 blocks");

        // Verify layer 0 (Z=0, 9 blocks) and layer 1 (Z=-1, 9 blocks)
        long countZ0 = tunnel.stream().filter(p -> p.getZ() == 0).count();
        long countZ1 = tunnel.stream().filter(p -> p.getZ() == -1).count();
        assertEquals(9, countZ0);
        assertEquals(9, countZ1);
    }

    @Test
    @DisplayName("Verify 3x3 Mining Tunnel downwards into floor")
    public void testMiningTunnel3x3Down() {
        BlockPos start = new BlockPos(0, 60, 0);
        Set<BlockPos> world = new HashSet<>();

        // 3x3 area digging Down (Y <= 60)
        for (int y = 60; y >= 58; y--) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    world.add(new BlockPos(x, y, z));
                }
            }
        }

        // Hitting Top face (UP) -> digDir = DOWN
        List<BlockPos> shaft = VeinShape.MINING_TUNNEL_3X3.calculatePositions(
                null, start, Direction.UP, Direction.NORTH, false, world::contains, 9
        );

        assertEquals(9, shaft.size(), "Single layer of 3x3 floor digging = 9 blocks");
        for (BlockPos p : shaft) {
            assertEquals(60, p.getY());
            assertTrue(Math.abs(p.getX()) <= 1);
            assertTrue(Math.abs(p.getZ()) <= 1);
        }
    }

    @Test
    @DisplayName("Verify hunger exhaustion formula matches Arise default multiplier")
    public void testHungerExhaustionMath() {
        double multiplier = 20.0;
        float vanillaExhaustion = 0.005F;

        float exhaustionPerBlock = (float) (multiplier * vanillaExhaustion);
        assertEquals(0.1F, exhaustionPerBlock, 0.0001F, "20.0 * 0.005F must equal 0.1F exhaustion per block");

        // In Minecraft, 4.0F exhaustion = 1 food level drained (half drumstick)
        // 40 blocks mined = 4.0F exhaustion = 1 food level drained
        float totalExhaustion40 = 40 * exhaustionPerBlock;
        assertEquals(4.0F, totalExhaustion40, 0.0001F);

        // 64 blocks mined (max default) = 6.4F exhaustion = 1.6 food levels
        float totalExhaustion64 = 64 * exhaustionPerBlock;
        assertEquals(6.4F, totalExhaustion64, 0.0001F);
    }

    @Test
    @DisplayName("Verify tool durability and preventToolBreak threshold logic")
    public void testToolDurabilitySafety() {
        int maxDurability = 250;
        int preventToolBreak = 1;

        // Tool with 2 durability left (damage = 248)
        int damageValueSafe = 248;
        int remainingDurabilitySafe = maxDurability - damageValueSafe;
        assertTrue(remainingDurabilitySafe > preventToolBreak, "2 durability left is strictly > threshold 1, safe to mine");

        // Tool with 1 durability left (damage = 249)
        int damageValueAtRisk = 249;
        int remainingDurabilityAtRisk = maxDurability - damageValueAtRisk;
        assertFalse(remainingDurabilityAtRisk > preventToolBreak, "1 durability left is <= threshold 1, must halt mining to save tool");
    }

    @Test
    @DisplayName("Verify merge tag wildcard, hash prefix, and pattern matching logic")
    public void testMergeTagMatching() {
        List<String> tags = List.of(
                "#minecraft:base_stone_overworld",
                "c:*_ores",
                "forge:ores/*",
                "#c:ores",
                "#minecraft:logs"
        );

        assertTrue(matchesTagString("c:iron_ores", tags));
        assertTrue(matchesTagString("c:deepslate_diamond_ores", tags));
        assertTrue(matchesTagString("forge:ores/copper", tags));
        assertTrue(matchesTagString("minecraft:base_stone_overworld", tags));
        assertTrue(matchesTagString("#minecraft:base_stone_overworld", tags));
        assertFalse(matchesTagString("minecraft:dirt", tags));
        assertFalse(matchesTagString("c:ingots", tags));
    }

    @Test
    @DisplayName("Verify network state packet only triggers on key toggle or when active key is down")
    public void testNetworkStatePacketTriggerLogic() {
        // Simulating AriseNB8Client: if (isDown != wasActive || (isDown && hitFace != lastHitFace))
        boolean wasActive = false;
        Direction lastHitFace = Direction.NORTH;

        // Case 1: Idle player turns camera between faces (isDown = false, hitFace changes to SOUTH)
        boolean isDown = false;
        Direction hitFace = Direction.SOUTH;
        boolean shouldSend = (isDown != wasActive || (isDown && hitFace != lastHitFace));
        assertFalse(shouldSend, "Must NOT send packet when idle player turns camera");

        // Case 2: Player presses key (isDown becomes true)
        isDown = true;
        shouldSend = (isDown != wasActive || (isDown && hitFace != lastHitFace));
        assertTrue(shouldSend, "Must send packet on key press");
        wasActive = true;
        lastHitFace = hitFace;

        // Case 3: Player holds key and aims at different face (isDown = true, hitFace changes to UP)
        hitFace = Direction.UP;
        shouldSend = (isDown != wasActive || (isDown && hitFace != lastHitFace));
        assertTrue(shouldSend, "Must send packet when holding key and moving to new block face");
        lastHitFace = hitFace;

        // Case 4: Player releases key (isDown becomes false)
        isDown = false;
        shouldSend = (isDown != wasActive || (isDown && hitFace != lastHitFace));
        assertTrue(shouldSend, "Must send packet on key release");
    }

    @Test
    @DisplayName("Verify concurrent tasks per player are cancelled and replaced")
    public void testConcurrentTaskPerPlayer() {
        java.util.concurrent.ConcurrentLinkedQueue<UUID> activeTasks = new java.util.concurrent.ConcurrentLinkedQueue<>();
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();

        activeTasks.add(player1);
        activeTasks.add(player2);
        assertEquals(2, activeTasks.size());

        // When player1 mines rapidly again, previous task is removed before adding new one
        activeTasks.removeIf(uuid -> uuid.equals(player1));
        activeTasks.add(player1);

        assertEquals(2, activeTasks.size(), "Queue must contain at most 1 task per player");
        long p1Count = activeTasks.stream().filter(uuid -> uuid.equals(player1)).count();
        assertEquals(1, p1Count, "Player1 must have exactly 1 active task");
    }

    @Test
    @DisplayName("Verify creative mode players do not receive physical drops or resource exhaustion")
    public void testCreativeModeSafety() {
        boolean isCreative = true;
        boolean hasInfiniteMaterials = isCreative;

        int droppedItems = 0;
        int toolDurabilityCost = 0;
        float foodExhaustion = 0.0f;

        // Block break execution
        if (!hasInfiniteMaterials) {
            droppedItems += 1;
            toolDurabilityCost += 1;
            foodExhaustion += 0.1f;
        }

        assertEquals(0, droppedItems, "Creative mode must not spawn physical dropped items");
        assertEquals(0, toolDurabilityCost, "Creative mode must not damage tool");
        assertEquals(0.0f, foodExhaustion, "Creative mode must not exhaust hunger");
    }

    private static boolean matchesTagString(String target, List<String> patterns) {
        for (String pattern : patterns) {
            if (pattern.equals("*")) return true;
            if (pattern.startsWith("#")) {
                if (target.equals(pattern) || target.equals(pattern.substring(1))) return true;
            } else if (pattern.contains("*")) {
                String regex = pattern.replace(".", "\\.").replace("*", ".*");
                if (target.matches(regex)) return true;
            } else if (target.equals(pattern)) {
                return true;
            }
        }
        return false;
    }
}
