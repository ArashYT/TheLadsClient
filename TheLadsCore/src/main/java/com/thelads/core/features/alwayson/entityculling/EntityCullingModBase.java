package com.thelads.core.features.alwayson.entityculling;

import com.thelads.core.features.alwayson.entityculling.occlusionculling.OcclusionCullingInstance;
import com.thelads.core.features.alwayson.entityculling.CullTask;
import com.thelads.core.features.alwayson.entityculling.Provider;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.StreamSupport;

public class EntityCullingModBase {
    public static final Logger LOGGER = LogManager.getLogger("EntityCulling");
    public static EntityCullingModBase instance;
    public OcclusionCullingInstance culling;
    public static boolean enabled = true;
    public boolean debugHitboxes = false;
    protected Thread cullThread;
    protected boolean lateInit = false;
    public Config config;
    public Set<BlockEntityType<?>> blockEntityWhitelist = new HashSet<>();
    public Set<EntityType<?>> entityWhitelist = new HashSet<>();
    public Set<EntityType<?>> tickCullWhitelists = new HashSet<>();
    public CullTask cullTask;
    public double lastTickTime = 0.0;
    public Frustum frustum = null;
    public DebugCollector debugCollector = new DebugCollector();
    
    public int renderedBlockEntities = 0;
    public int skippedBlockEntities = 0;
    public int renderedEntities = 0;
    public int skippedEntities = 0;
    public int tickedEntities = 0;
    public int skippedEntityTicks = 0;
    private int tickCounter = 0;

    public void onInitialize() {
        instance = this;
        this.config = new Config();
        this.culling = new OcclusionCullingInstance(this.config.tracingDistance, new Provider());
        this.cullTask = new CullTask(this.culling, this.blockEntityWhitelist, this.entityWhitelist);
        this.cullThread = new Thread(this.cullTask, "CullThread");
        this.cullThread.setUncaughtExceptionHandler((thread, ex) -> LOGGER.error("The CullingThread has crashed!", ex));
    }

    public void clientTick() {
        if (!this.lateInit) {
            this.lateInit = true;
            this.cullThread.start();
            for (String blockId : this.config.blockEntityWhitelist) {
                Optional<? extends BlockEntityType<?>> block = BuiltInRegistries.BLOCK_ENTITY_TYPE.getOptional(Identifier.parse(blockId));
                block.ifPresent(b -> this.blockEntityWhitelist.add(b));
            }
            for (String entityType : this.config.tickCullingWhitelist) {
                Optional<? extends EntityType<?>> entity = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse(entityType));
                entity.ifPresent(e -> this.tickCullWhitelists.add(e));
            }
            for (String entityType : this.config.entityWhitelist) {
                Optional<? extends EntityType<?>> entity = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse(entityType));
                entity.ifPresent(e -> this.entityWhitelist.add(e));
            }
        }
        long start = System.nanoTime();
        Minecraft client = Minecraft.getInstance();
        boolean ingame = client.level != null && client.player != null && client.player.tickCount > 10;
        if (ingame && enabled) {
            boolean changed = false;
            if (this.tickCounter++ % this.config.captureRate == 0) {
                if (!this.config.skipEntityCulling) {
                    List<Entity> entities = StreamSupport.stream(client.level.entitiesForRendering().spliterator(), false).toList();
                    this.cullTask.setEntitiesForRendering(entities);
                }
                if (!this.config.skipBlockEntityCulling) {
                    HashMap<BlockPos, BlockEntity> blockEntities = new HashMap<>();
                    for (int x = -8; x <= 8; ++x) {
                        for (int z = -8; z <= 8; ++z) {
                            LevelChunk chunk = client.level.getChunk(client.player.chunkPosition().x() + x, client.player.chunkPosition().z() + z);
                            blockEntities.putAll(chunk.getBlockEntities());
                        }
                    }
                    this.cullTask.setBlockEntities(blockEntities);
                }
                changed = true;
            }
            this.cullTask.setIngame(true);
            this.cullTask.setCameraMC(client.gameRenderer.mainCamera().position());
            this.cullTask.requestCull = true;
            if (changed) {
                this.lastTickTime = (double)(System.nanoTime() - start) / 1000000.0;
            }
        } else {
            this.cullTask.setIngame(false);
            this.cullTask.setEntitiesForRendering(Collections.emptyList());
            this.cullTask.setBlockEntities(Collections.emptyMap());
            this.lastTickTime = (double)(System.nanoTime() - start) / 1000000.0;
        }
    }

    public AABB setupAABB(BlockEntity entity, BlockPos pos) {
        if (entity instanceof BannerBlockEntity) {
            return new AABB(pos).inflate(0.0, 1.0, 0.0);
        }
        return new AABB(pos);
    }

    public boolean isDynamicWhitelisted(BlockEntity entity) {
        return false;
    }

    public boolean isDynamicWhitelisted(Entity entity) {
        return false;
    }
}
