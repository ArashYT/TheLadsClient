package com.thelads.core.v26_2.feature.ebe;

import com.mojang.blaze3d.platform.Transparency;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thelads.core.client.ebe.BakedBlockEntities;
import com.thelads.core.client.ebe.BakedQuadOrder;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.model.object.bell.BellModel;
import net.minecraft.client.model.object.chest.ChestModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.blockentity.BellRenderer;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.minecraft.client.renderer.blockentity.DecoratedPotRenderer;
import net.minecraft.client.renderer.blockentity.ShulkerBoxRenderer;
import net.minecraft.client.renderer.blockentity.state.ChestRenderState.ChestMaterialType;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CopperChestBlock;
import net.minecraft.world.level.block.DecoratedPotBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ChestLidController;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.PotDecorations;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 26.3 side of {@link BakedBlockEntities}. Vanilla 26.3 already draws beds and signs as block models; chests (all kinds),
 * shulker boxes and the bell body are still entity-style, so those get baked block models here. The geometry is
 * captured from vanilla's own entity models and renderer transforms (exact shape and UVs), its textures are stitched
 * into the block atlas (assets/minecraft/atlases/blocks.json), and the models replace the particle-only vanilla ones
 * in the block-state model map during each model reload. Per-position switching goes through Fabric's renderer API
 * (vanilla's Indigo and Sodium both call {@code emitQuads} with the position).
 */
public final class BakedBlockEntities263 {
    private static final Logger LOG = LoggerFactory.getLogger("TheLadsCore/BakedBlockEntities");
    private static final Direction[] DIRECTIONS = Direction.values();

    private static volatile SpriteLoader.@Nullable Preparations blockAtlas;
    private static volatile @Nullable EntityModelSet entityModels;
    private static volatile Map<BlockState, BakedModel> pending = Map.of();
    /** Block states drawn baked right now (set on the main thread when the reload applies). */
    private static volatile Map<BlockState, BakedModel> installed = Map.of();
    private static boolean initialised;

    private BakedBlockEntities263() {}

    /** Client entrypoint. */
    public static synchronized void init() {
        if (initialised) return;
        initialised = true;
        if (BakedBlockEntities.init()) LOG.info("Baked block entities on (chests, shulker boxes, bells, decorated pots)");
        else LOG.info("Baked block entities standing down: {}", BakedBlockEntities.standDownReason());
        BakedBlockEntitiesProbe263.register();
    }

    static int bakedStates() { return installed.size(); }

    // ---- model reload (worker thread) ----------------------------------------------------------------------------

    /** ModelManager.loadModels: the freshly stitched block atlas and entity models of this reload. */
    public static void prepare(SpriteLoader.Preparations atlas, EntityModelSet models) {
        blockAtlas = atlas;
        entityModels = models;
    }

    /** ModelManager.createBlockStateToModelDispatch: add baked models to the state -> model map being built. */
    public static void bake(Map<BlockState, BlockStateModel> byState) {
        init();
        Map<BlockState, BakedModel> ours = new IdentityHashMap<>();
        SpriteLoader.Preparations atlas = blockAtlas;
        EntityModelSet models = entityModels;
        if (BakedBlockEntities.active() && atlas != null && models != null) {
            try {
                new Baker(atlas, models, Minecraft.getInstance().getResourceManager(), byState, ours).run();
            } catch (Throwable t) {
                LOG.error("Baked block entities disabled for this reload", t);
                ours.clear();
            }
        }
        byState.putAll(ours);
        pending = ours;
        blockAtlas = null;
        entityModels = null;
    }

    /** ModelManager.apply (main thread): the new models are live; the level re-meshes every section after a reload. */
    public static void apply() {
        installed = pending;
        BakedBlockEntities.clear();
        if (!installed.isEmpty()) LOG.info("Baked block entities: {} block states", installed.size());
    }

    // ---- block entity render path (main thread) ------------------------------------------------------------------

    /**
     * BlockEntityRenderDispatcher.tryExtractRenderState: keeps the switch state of a baked block entity current and
     * tells whether its vanilla renderer must be skipped this frame (the chunk mesh shows it).
     */
    public static boolean skipRenderer(BlockEntity entity) {
        Map<BlockState, BakedModel> map = installed;
        if (map.isEmpty() || !map.containsKey(entity.getBlockState())) return false;
        if (entity.getLevel() == null || entity.getLevel() != Minecraft.getInstance().level) return false; // only the client level's sections hold baked models (not item, preview or mod levels)
        BlockPos pos = entity.getBlockPos();
        long key = pos.asLong();
        if (BakedBlockEntities.setAnimating(key, animating(entity))) markDirty(pos);
        return !BakedBlockEntities.drawDynamic(key);
    }

    private static boolean animating(BlockEntity entity) {
        if (entity instanceof ChestBlockEntity chest) {
            if (lidMoving(((LidHolder) chest).lads$lid())) return true;
            BlockState state = chest.getBlockState();
            if (state.hasProperty(ChestBlock.TYPE) && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE && chest.getLevel() != null) {
                BlockEntity other = chest.getLevel().getBlockEntity(chest.getBlockPos().relative(ChestBlock.getConnectedDirection(state)));
                return other instanceof ChestBlockEntity half && lidMoving(((LidHolder) half).lads$lid());
            }
            return false;
        }
        if (entity instanceof EnderChestBlockEntity ender) return lidMoving(((LidHolder) ender).lads$lid());
        if (entity instanceof ShulkerBoxBlockEntity box) {
            return box.getAnimationStatus() != ShulkerBoxBlockEntity.AnimationStatus.CLOSED || box.getProgress(1f) > 0f;
        }
        if (entity instanceof BellBlockEntity bell) return bell.shaking;
        if (entity instanceof DecoratedPotBlockEntity pot) return potDynamic(pot);
        return false;
    }

    /**
     * Decorated pots are baked with plain sides; one with pottery sherds (per block entity, not in the block state) or
     * wobbling after a hit keeps its renderer.
     */
    private static boolean potDynamic(DecoratedPotBlockEntity pot) {
        DecoratedPotBlockEntity.WobbleStyle wobble = pot.lastWobbleStyle;
        if (wobble != null && pot.getLevel() != null && pot.getLevel().getGameTime() - pot.wobbleStartedAtTick <= wobble.duration) return true;
        PotDecorations sides = pot.getDecorations();
        return patterned(sides.back()) || patterned(sides.left()) || patterned(sides.right()) || patterned(sides.front());
    }

    private static boolean patterned(Optional<? extends ItemInstance> side) {
        return side.isPresent() && side.get().get(DataComponents.PROVIDES_POTTERY_PATTERN) != null;
    }

    /** DecoratedPotBlockEntity.loadAdditional (client): a sherd pot arriving with its chunk is meshed without its baked sides. */
    public static void potLoaded(DecoratedPotBlockEntity pot) {
        if (installed.containsKey(pot.getBlockState()) && potDynamic(pot) && BakedBlockEntities.setAnimating(pot.getBlockPos().asLong(), true)
            && pot.getLevel() != null) markDirty(pot.getBlockPos());
    }

    private static boolean lidMoving(ChestLidController lid) {
        return ((LidTarget) lid).lads$shouldBeOpen() || lid.getOpenness(1f) > 0f || lid.getOpenness(0f) > 0f;
    }

    private static void markDirty(BlockPos pos) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        int x = SectionPos.blockToSectionCoord(pos.getX()), y = SectionPos.blockToSectionCoord(pos.getY()),
            z = SectionPos.blockToSectionCoord(pos.getZ());
        level.setSectionRangeDirty(x, y, z, x, y, z);
    }

    /** Added to the (ender) chest block entities by mixin: their private lid controller. */
    public interface LidHolder { ChestLidController lads$lid(); }

    /** Added to ChestLidController by mixin: whether an open event arrived (the lid starts moving next tick). */
    public interface LidTarget {
        boolean lads$shouldBeOpen();

        /** QA probe only: hold the lid at {@code openness} (0..1) until the next tick moves it. */
        void lads$force(float openness);
    }

    // ---- baking ----------------------------------------------------------------------------------------------------

    private static final class Baker {
        private final SpriteLoader.Preparations atlas;
        private final EntityModelSet models;
        private final ResourceManager resources;
        private final Map<BlockState, BlockStateModel> vanilla;
        private final Map<BlockState, BakedModel> out;
        private final Capture capture = new Capture();

        Baker(SpriteLoader.Preparations atlas, EntityModelSet models, ResourceManager resources,
              Map<BlockState, BlockStateModel> vanilla, Map<BlockState, BakedModel> out) {
            this.atlas = atlas;
            this.models = models;
            this.resources = resources;
            this.vanilla = vanilla;
            this.out = out;
        }

        void run() {
            boolean xmas = ChestRenderer.xmasTextures();
            boolean chests = !PackOverrides.replaces(resources, PackOverrides.CHESTS);
            boolean shulkers = !PackOverrides.replaces(resources, PackOverrides.SHULKERS);
            boolean bells = !PackOverrides.replaces(resources, PackOverrides.BELLS);
            boolean pots = !PackOverrides.replaces(resources, PackOverrides.POTS);
            Map<ChestType, ChestModel> chestModels = new IdentityHashMap<>();
            ModelPart shulker = shulkers ? models.bakeLayer(ModelLayers.SHULKER_BOX) : null;
            List<BakedQuad> bellBody = null;
            for (Block block : BuiltInRegistries.BLOCK) {
                // Only vanilla's own blocks: modded chests (Lootr, Quark...) subclass these and keep their renderers.
                if (!"minecraft".equals(BuiltInRegistries.BLOCK.getKey(block).getNamespace())) continue;
                if (chests && (block instanceof ChestBlock || block instanceof EnderChestBlock)) {
                    for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                        ChestType type = state.hasProperty(ChestBlock.TYPE) ? state.getValue(ChestBlock.TYPE) : ChestType.SINGLE;
                        TextureAtlasSprite sprite = sprite(Sheets.chooseSprite(chestMaterial(block, xmas), type));
                        if (sprite == null) break;
                        ChestModel model = chestModels.computeIfAbsent(type, t -> new ChestModel(models.bakeLayer(ChestRenderer.LAYERS.select(t))));
                        model.setupAnim(0f);
                        PoseStack pose = new PoseStack();
                        pose.mulPose(ChestRenderer.modelTransformation(state.getValue(ChestBlock.FACING)));
                        capture.begin();
                        model.renderToBuffer(pose, capture, 0, 0, -1);
                        put(state, capture.quads(sprite), false);
                    }
                } else if (shulker != null && block instanceof ShulkerBoxBlock box) {
                    DyeColor color = box.getColor();
                    TextureAtlasSprite sprite = sprite(color == null ? Sheets.DEFAULT_SHULKER_TEXTURE_LOCATION : Sheets.getShulkerBoxSprite(color));
                    if (sprite == null) continue;
                    for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                        shulker.getAllParts().forEach(ModelPart::resetPose);
                        ModelPart lid = shulker.getChild("lid");
                        lid.setPos(0f, 24f, 0f); // ShulkerBoxModel.setupAnim at progress 0
                        lid.yRot = 0f;
                        PoseStack pose = new PoseStack();
                        pose.mulPose(ShulkerBoxRenderer.modelTransform(state.getValue(ShulkerBoxBlock.FACING)));
                        capture.begin();
                        shulker.render(pose, capture, 0, 0, -1);
                        put(state, capture.quads(sprite), false);
                    }
                } else if (pots && block == Blocks.DECORATED_POT) {
                    TextureAtlasSprite base = sprite(Sheets.DECORATED_POT_BASE), side = sprite(Sheets.DECORATED_POT_SIDE);
                    if (base == null || side == null) continue;
                    ModelPart potBase = models.bakeLayer(ModelLayers.DECORATED_POT_BASE), potSides = models.bakeLayer(ModelLayers.DECORATED_POT_SIDES);
                    for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                        PoseStack pose = new PoseStack();
                        pose.mulPose(DecoratedPotRenderer.modelTransformation(state.getValue(DecoratedPotBlock.HORIZONTAL_FACING)));
                        List<BakedQuad> quads = new ArrayList<>();
                        capture.begin();
                        for (String name : new String[] {"neck", "top", "bottom"}) potBase.getChild(name).render(pose, capture, 0, 0);
                        quads.addAll(capture.quads(base));
                        capture.begin();
                        for (String name : new String[] {"front", "back", "left", "right"}) potSides.getChild(name).render(pose, capture, 0, 0);
                        quads.addAll(capture.quads(side));
                        put(state, List.copyOf(quads), false);
                    }
                } else if (bells && block == Blocks.BELL) {
                    TextureAtlasSprite sprite = sprite(BellRenderer.BELL_TEXTURE);
                    if (sprite == null) continue;
                    BellModel model = new BellModel(models.bakeLayer(ModelLayers.BELL));
                    model.setupAnim(new BellModel.State(0f, null));
                    capture.begin();
                    model.renderToBuffer(new PoseStack(), capture, 0, 0, -1);
                    bellBody = capture.quads(sprite);
                    for (BlockState state : block.getStateDefinition().getPossibleStates()) put(state, bellBody, true);
                }
            }
        }

        private void put(BlockState state, List<BakedQuad> quads, boolean keepVanillaParts) {
            BlockStateModel original = vanilla.get(state);
            if (original == null || quads.isEmpty()) return;
            out.put(state, new BakedModel(original, new Part(quads, original.particleMaterial()), keepVanillaParts));
        }

        private @Nullable TextureAtlasSprite sprite(SpriteId id) {
            TextureAtlasSprite sprite = atlas.getSprite(id.texture());
            if (sprite == null) LOG.warn("Baked block entities: {} is not in the block atlas, leaving it to its renderer", id.texture());
            return sprite;
        }

        private static ChestMaterialType chestMaterial(Block block, boolean xmas) {
            if (block instanceof EnderChestBlock) return ChestMaterialType.ENDER_CHEST;
            if (block instanceof CopperChestBlock copper) {
                return switch (copper.getState()) {
                    case UNAFFECTED -> ChestMaterialType.COPPER_UNAFFECTED;
                    case EXPOSED -> ChestMaterialType.COPPER_EXPOSED;
                    case WEATHERED -> ChestMaterialType.COPPER_WEATHERED;
                    case OXIDIZED -> ChestMaterialType.COPPER_OXIDIZED;
                };
            }
            if (xmas) return ChestMaterialType.CHRISTMAS;
            return block == Blocks.TRAPPED_CHEST ? ChestMaterialType.TRAPPED : ChestMaterialType.REGULAR;
        }
    }

    /** Records what an entity model draws: quads of block-space positions and texture-space (0..1) UVs. */
    private static final class Capture implements VertexConsumer {
        private final List<float[]> vertices = new ArrayList<>();

        void begin() { vertices.clear(); }

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz) {
            vertices.add(new float[] {x, y, z, u, v});
        }

        List<BakedQuad> quads(TextureAtlasSprite sprite) {
            List<BakedQuad> quads = new ArrayList<>(vertices.size() / 4);
            Material.Baked material = new Material.Baked(sprite, false);
            BakedQuad.MaterialInfo info = BakedQuad.MaterialInfo.of(material, Transparency.TRANSPARENT, -1, null, 0);
            for (int q = 0; q + 3 < vertices.size(); q += 4) {
                float[] p = new float[12];
                for (int v = 0; v < 4; v++) System.arraycopy(vertices.get(q + v), 0, p, v * 3, 3);
                if (BakedQuadOrder.area2(p) < 1e-7f) continue;
                int direction = BakedQuadOrder.direction(p);
                int[] order = BakedQuadOrder.order(p, direction);
                Vector3f[] pos = new Vector3f[4];
                long[] uv = new long[4];
                for (int i = 0; i < 4; i++) {
                    float[] vertex = vertices.get(q + order[i]);
                    pos[i] = new Vector3f(vertex[0], vertex[1], vertex[2]);
                    uv[i] = UVPair.pack(sprite.getU(vertex[3]), sprite.getV(vertex[4]));
                }
                quads.add(new BakedQuad(pos[0], pos[1], pos[2], pos[3], uv[0], uv[1], uv[2], uv[3], DIRECTIONS[direction], info));
            }
            return List.copyOf(quads);
        }

        // Model parts only use the bulk addVertex above; the single-element calls are never reached.
        @Override public VertexConsumer addVertex(float x, float y, float z) { return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer setColor(int color) { return this; }
        @Override public VertexConsumer setUv(float u, float v) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setUv3(float u, float v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
        @Override public VertexConsumer setLineWidth(float width) { return this; }
    }

    /** The block entity's geometry as a block-model part (all quads unculled: none lies on a full block face). */
    private record Part(List<BakedQuad> quads, Material.Baked particle) implements BlockStateModelPart {
        @Override public List<BakedQuad> getQuads(@Nullable Direction direction) { return direction == null ? quads : List.of(); }
        // Flat-lit like the block entity renderer it replaces: smooth AO made baked chests darker (and much darker under shader packs).
        @Override public boolean useAmbientOcclusion() { return false; }
        @Override public Material.Baked particleMaterial() { return particle; }
        @Override public int materialFlags() {
            int flags = 0;
            for (BakedQuad quad : quads) flags |= quad.materialInfo().flags();
            return flags;
        }
    }

    /**
     * Baked block-state model: the vanilla model (particle texture; the bell's frame) plus the block entity part, which
     * is left out of the mesh while its position animates.
     */
    static final class BakedModel implements BlockStateModel {
        private final BlockStateModel vanilla;
        private final Part part;
        private final boolean keepVanillaParts;

        BakedModel(BlockStateModel vanilla, Part part, boolean keepVanillaParts) {
            this.vanilla = vanilla;
            this.part = part;
            this.keepVanillaParts = keepVanillaParts;
        }

        @Override
        public void collectParts(RandomSource random, List<BlockStateModelPart> output) {
            if (keepVanillaParts) vanilla.collectParts(random, output);
            output.add(part);
        }

        @Override
        public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
                              Predicate<@Nullable Direction> cullTest) {
            if (keepVanillaParts) vanilla.emitQuads(emitter, level, pos, state, random, cullTest);
            if (BakedBlockEntities.bake(pos.asLong())) part.emitQuads(emitter, cullTest);
        }

        @Override public Material.Baked particleMaterial() { return vanilla.particleMaterial(); }
        @Override public int materialFlags() { return vanilla.materialFlags() | part.materialFlags(); }
    }

    /**
     * Resource packs can swap these models or textures through OptiFine-format CEM / random / emissive files (read by
     * the embedded Entity Model/Texture Features); a baked model would ignore them, so those types keep their renderer.
     */
    static final class PackOverrides {
        static final String[] CHESTS = {"chest", "trapped_chest", "ender_chest", "chest_large", "trapped_chest_large",
            "copper_chest", "entity/chest/"};
        static final String[] SHULKERS = {"shulker_box", "entity/shulker/"};
        static final String[] BELLS = {"bell", "entity/bell/"};
        static final String[] POTS = {"decorated_pot", "entity/decorated_pot/"};

        static boolean replaces(ResourceManager resources, String[] keys) {
            for (String key : keys) {
                if (key.endsWith("/")) {
                    String dir = key.substring(0, key.length() - 1);
                    if (any(resources, "optifine/random/" + dir)) return true;
                    if (!resources.listResources("textures/" + dir, id -> id.getPath().endsWith("_e.png")).isEmpty()) return true;
                } else {
                    for (String root : new String[] {"optifine/cem/", "emf/cem/"}) {
                        if (resources.getResource(Identifier.withDefaultNamespace(root + key + ".jem")).isPresent()) {
                            LOG.info("Baked block entities: resource pack model {}{}.jem found, keeping the vanilla renderer", root, key);
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        private static boolean any(ResourceManager resources, String dir) {
            if (resources.listResources(dir, id -> true).isEmpty()) return false;
            LOG.info("Baked block entities: resource pack textures under {} found, keeping the vanilla renderer", dir);
            return true;
        }
    }
}
