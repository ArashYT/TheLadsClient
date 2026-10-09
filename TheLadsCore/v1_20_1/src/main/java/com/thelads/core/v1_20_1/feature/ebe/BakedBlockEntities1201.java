package com.thelads.core.v1_20_1.feature.ebe;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.thelads.core.client.ebe.BakedBlockEntities;
import com.thelads.core.client.ebe.BakedQuadOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ShulkerModel;
import net.minecraft.client.model.geom.LayerDefinitions;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.blockentity.BellRenderer;
import net.minecraft.client.renderer.blockentity.HangingSignRenderer;
import net.minecraft.client.renderer.blockentity.SignRenderer;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.DecoratedPotBlock;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.entity.DecoratedPotPatterns;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.BedBlockEntity;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ChestLidController;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.WoodType;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 1.20.1 side of {@link BakedBlockEntities}: chests (normal, trapped, Christmas, ender; single and double), shulker
 * boxes, beds, signs, hanging signs and the bell body become baked block models. Geometry and UVs are captured from
 * vanilla's own entity models through the same transforms its renderers use, the textures are stitched into the block
 * atlas (assets/minecraft/atlases/blocks.json), and the models are put into the block-state model cache on every
 * resource reload. Those states then report {@code RenderShape.MODEL}, so vanilla, Sodium and Embeddium mesh them.
 * Neither Sodium 0.5 nor vanilla gives a model the block position, so the position comes from {@code getSeed(pos)},
 * which all three call on the meshing thread right before asking the model for quads.
 */
public final class BakedBlockEntities1201 {
    private static final Logger LOG = LoggerFactory.getLogger("TheLadsCore/BakedBlockEntities");
    private static final Direction[] DIRECTIONS = Direction.values();
    /** Block position (BlockPos#asLong) of the baked block being meshed on this thread; set from BlockState#getSeed. */
    public static final ThreadLocal<long[]> MESH_POS = ThreadLocal.withInitial(() -> new long[] {Long.MIN_VALUE});
    private static volatile Map<BlockState, EbeModel> installed = Map.of();
    private static boolean initialised;
    /** Main thread, sign renderer: the board of the sign being rendered is in the chunk mesh (only its text is drawn). */
    public static boolean signBoardBaked;

    private BakedBlockEntities1201() {}

    /** Implemented on BlockState by mixin: baked states report RenderShape.MODEL and publish their meshing position. */
    public interface Flag {
        boolean lads$baked();

        void lads$setBaked(boolean baked);
    }

    /** Client entrypoint. */
    public static synchronized void init() {
        if (initialised) return;
        initialised = true;
        if (!BakedBlockEntities.init()) {
            LOG.info("Baked block entities standing down: {}", BakedBlockEntities.standDownReason());
            return;
        }
        LOG.info("Baked block entities on (chests, shulker boxes, beds, signs, bells, decorated pots)");
        // Cutout, as vanilla's entity renderers draw them: alpha-tested and without mipmaps. Bells keep their (solid) frame layer.
        // Only meshed while baked (the other states stay INVISIBLE / entity-rendered), so this is safe even when a type stands down.
        for (Block block : BuiltInRegistries.BLOCK) {
            if (candidate(block) && !(block instanceof BellBlock)) BlockRenderLayerMap.INSTANCE.putBlock(block, RenderType.cutout());
        }
        BakedBlockEntitiesProbe1201.register();
    }

    static int bakedStates() { return installed.size(); }

    private static boolean candidate(Block block) {
        if (!"minecraft".equals(BuiltInRegistries.BLOCK.getKey(block).getNamespace())) return false; // modded chests keep their renderers
        return block instanceof ChestBlock || block instanceof EnderChestBlock || block instanceof ShulkerBoxBlock || block instanceof BedBlock
            || block instanceof SignBlock || block instanceof BellBlock || block instanceof DecoratedPotBlock;
    }

    // ---- model reload (main thread, ModelManager.apply -> BlockModelShaper.replaceCache) ---------------------------

    /** The block-state model cache vanilla just built; returns it with the baked models added. */
    public static Map<BlockState, BakedModel> install(Map<BlockState, BakedModel> vanilla) {
        init();
        for (BlockState state : installed.keySet()) ((Flag) state).lads$setBaked(false);
        installed = Map.of();
        BakedBlockEntities.clear();
        if (!BakedBlockEntities.active()) return vanilla;
        Map<BlockState, EbeModel> ours = new IdentityHashMap<>();
        try {
            new Baker(vanilla, ours).run();
        } catch (Throwable t) {
            LOG.error("Baked block entities disabled for this reload", t);
            return vanilla;
        }
        if (ours.isEmpty()) return vanilla;
        Map<BlockState, BakedModel> merged = new HashMap<>(vanilla);
        merged.putAll(ours);
        for (BlockState state : ours.keySet()) ((Flag) state).lads$setBaked(true);
        installed = ours;
        LOG.info("Baked block entities: {} block states", ours.size());
        return merged;
    }

    // ---- block entity render path (main thread) ------------------------------------------------------------------

    /** BlockEntityRenderDispatcher.render: true if the chunk mesh shows this block entity, so its renderer is skipped. */
    public static boolean skipRenderer(BlockEntity entity) {
        BlockState state = entity.getBlockState();
        if (!((Flag) state).lads$baked() || entity instanceof SignBlockEntity) return false; // signs: only the board is baked
        BlockPos pos = entity.getBlockPos();
        long key = pos.asLong();
        if (BakedBlockEntities.setAnimating(key, animating(entity))) markDirty(pos);
        return !BakedBlockEntities.drawDynamic(key);
    }

    /** SignRenderer: is the board of this sign in the chunk mesh (the renderer then draws only the text)? */
    public static boolean signBoardBaked(SignBlockEntity sign) {
        return ((Flag) sign.getBlockState()).lads$baked() && !BakedBlockEntities.drawDynamic(sign.getBlockPos().asLong());
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
        if (entity instanceof DecoratedPotBlockEntity pot) return patterned(pot);
        return false; // beds never move
    }

    /** Pots are baked with plain sides; one with pottery sherds (block entity data, not block state) keeps its renderer. */
    private static boolean patterned(DecoratedPotBlockEntity pot) {
        DecoratedPotBlockEntity.Decorations sides = pot.getDecorations();
        return patterned(sides.back()) || patterned(sides.left()) || patterned(sides.right()) || patterned(sides.front());
    }

    private static boolean patterned(Item side) {
        return side != Items.BRICK && DecoratedPotPatterns.getResourceKey(side) != null;
    }

    /** DecoratedPotBlockEntity.load (client): a sherd pot arriving with its chunk is meshed without its baked sides. */
    public static void potLoaded(DecoratedPotBlockEntity pot) {
        if (((Flag) pot.getBlockState()).lads$baked() && patterned(pot) && BakedBlockEntities.setAnimating(pot.getBlockPos().asLong(), true)
            && pot.getLevel() != null) markDirty(pot.getBlockPos());
    }

    private static boolean lidMoving(ChestLidController lid) {
        return ((LidTarget) lid).lads$shouldBeOpen() || lid.getOpenness(1f) > 0f || lid.getOpenness(0f) > 0f;
    }

    private static void markDirty(BlockPos pos) {
        Minecraft.getInstance().levelRenderer.setSectionDirty(SectionPos.blockToSectionCoord(pos.getX()),
            SectionPos.blockToSectionCoord(pos.getY()), SectionPos.blockToSectionCoord(pos.getZ()));
    }

    /** Added to the (ender) chest block entities by mixin: their private lid controller. */
    public interface LidHolder { ChestLidController lads$lid(); }

    /** Added to ChestLidController by mixin. */
    public interface LidTarget {
        boolean lads$shouldBeOpen();

        /** QA probe only: hold the lid at {@code openness} (0..1) until the next tick moves it. */
        void lads$force(float openness);
    }

    // ---- baking ----------------------------------------------------------------------------------------------------

    private static final class Baker {
        private final Map<BlockState, BakedModel> vanilla;
        private final Map<BlockState, EbeModel> out;
        private final Map<ModelLayerLocation, LayerDefinition> layers = LayerDefinitions.createRoots();
        private final TextureAtlas atlas = Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS);
        private final Capture capture = new Capture();

        Baker(Map<BlockState, BakedModel> vanilla, Map<BlockState, EbeModel> out) {
            this.vanilla = vanilla;
            this.out = out;
        }

        void run() {
            ResourceManager resources = Minecraft.getInstance().getResourceManager();
            boolean xmas = BakedBlockEntities.christmas();
            boolean chests = !PackOverrides.replaces(resources, PackOverrides.CHESTS);
            boolean shulkers = !PackOverrides.replaces(resources, PackOverrides.SHULKERS);
            boolean beds = !PackOverrides.replaces(resources, PackOverrides.BEDS);
            // Amendments (Zero SMP) changes how signs, hanging signs and bells render (swinging signs, bell ropes): keep theirs.
            boolean amendments = BakedBlockEntities.modLoaded("amendments");
            boolean signs = !amendments && !PackOverrides.replaces(resources, PackOverrides.SIGNS);
            boolean bells = !amendments && !PackOverrides.replaces(resources, PackOverrides.BELLS);
            boolean pots = !PackOverrides.replaces(resources, PackOverrides.POTS);
            ModelPart potBase = part(ModelLayers.DECORATED_POT_BASE), potSides = part(ModelLayers.DECORATED_POT_SIDES);
            Material potBaseMaterial = Sheets.getDecoratedPotMaterial(DecoratedPotPatterns.BASE);
            Material potSideMaterial = Sheets.getDecoratedPotMaterial(DecoratedPotPatterns.getResourceKey(Items.BRICK));
            if (amendments) LOG.info("Baked block entities: Amendments installed, signs, hanging signs and bells keep their renderers");
            ModelPart chestSingle = part(ModelLayers.CHEST), chestLeft = part(ModelLayers.DOUBLE_CHEST_LEFT), chestRight = part(ModelLayers.DOUBLE_CHEST_RIGHT);
            ShulkerModel<?> shulker = new ShulkerModel<>(part(ModelLayers.SHULKER));
            ModelPart bedHead = part(ModelLayers.BED_HEAD), bedFoot = part(ModelLayers.BED_FOOT);
            ModelPart bellBody = part(ModelLayers.BELL).getChild("bell_body");
            for (Block block : BuiltInRegistries.BLOCK) {
                if (!candidate(block)) continue;
                for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                    PoseStack pose = new PoseStack();
                    capture.begin();
                    Material material;
                    boolean keepVanilla = false;
                    if (block instanceof ChestBlock || block instanceof EnderChestBlock) {
                        if (!chests) break;
                        ChestType type = state.hasProperty(ChestBlock.TYPE) ? state.getValue(ChestBlock.TYPE) : ChestType.SINGLE;
                        BlockEntity dummy = ((EntityBlock) block).newBlockEntity(BlockPos.ZERO, state);
                        material = Sheets.chooseMaterial(dummy, type, xmas);
                        ModelPart root = type == ChestType.LEFT ? chestLeft : type == ChestType.RIGHT ? chestRight : chestSingle;
                        pose.translate(0.5F, 0.5F, 0.5F);
                        pose.mulPose(Axis.YP.rotationDegrees(-state.getValue(ChestBlock.FACING).toYRot()));
                        pose.translate(-0.5F, -0.5F, -0.5F);
                        root.getAllParts().forEach(ModelPart::resetPose);
                        root.getChild("lid").render(pose, capture, 0, 0);
                        root.getChild("lock").render(pose, capture, 0, 0);
                        root.getChild("bottom").render(pose, capture, 0, 0);
                    } else if (block instanceof ShulkerBoxBlock box) {
                        if (!shulkers) break;
                        DyeColor color = box.getColor();
                        material = color == null ? Sheets.DEFAULT_SHULKER_TEXTURE_LOCATION : Sheets.SHULKER_TEXTURE_LOCATION.get(color.getId());
                        pose.translate(0.5F, 0.5F, 0.5F);
                        pose.scale(0.9995F, 0.9995F, 0.9995F);
                        pose.mulPose(state.getValue(ShulkerBoxBlock.FACING).getRotation());
                        pose.scale(1.0F, -1.0F, -1.0F);
                        pose.translate(0.0F, -1.0F, 0.0F);
                        shulker.getLid().setPos(0.0F, 24.0F, 0.0F);
                        shulker.getLid().yRot = 0.0F;
                        shulker.renderToBuffer(pose, capture, 0, 0, 1f, 1f, 1f, 1f);
                    } else if (block instanceof BedBlock bed) {
                        if (!beds) break;
                        material = Sheets.BED_TEXTURES[bed.getColor().getId()];
                        pose.translate(0.0F, 0.5625F, 0.0F);
                        pose.mulPose(Axis.XP.rotationDegrees(90.0F));
                        pose.translate(0.5F, 0.5F, 0.5F);
                        pose.mulPose(Axis.ZP.rotationDegrees(180.0F + state.getValue(BedBlock.FACING).toYRot()));
                        pose.translate(-0.5F, -0.5F, -0.5F);
                        (state.getValue(BedBlock.PART) == BedPart.HEAD ? bedHead : bedFoot).render(pose, capture, 0, 0);
                    } else if (block instanceof SignBlock sign) {
                        if (!signs) break;
                        WoodType wood = SignBlock.getWoodType(block);
                        float rotation = -sign.getYRotationDegrees(state);
                        if (block instanceof CeilingHangingSignBlock || block instanceof WallHangingSignBlock) {
                            material = Sheets.getHangingSignMaterial(wood);
                            HangingSignRenderer.HangingSignModel model = new HangingSignRenderer.HangingSignModel(part(ModelLayers.createHangingSignModelName(wood)));
                            model.evaluateVisibleParts(state);
                            pose.translate(0.5, 0.9375, 0.5);
                            pose.mulPose(Axis.YP.rotationDegrees(rotation));
                            pose.translate(0.0F, -0.3125F, 0.0F);
                            pose.scale(1.0F, -1.0F, -1.0F);
                            model.root.render(pose, capture, 0, 0);
                        } else if (block instanceof StandingSignBlock || block instanceof WallSignBlock) {
                            material = Sheets.getSignMaterial(wood);
                            SignRenderer.SignModel model = new SignRenderer.SignModel(part(ModelLayers.createSignModelName(wood)));
                            model.stick.visible = block instanceof StandingSignBlock;
                            float scale = 0.6666667F;
                            pose.translate(0.5F, 0.75F * scale, 0.5F);
                            pose.mulPose(Axis.YP.rotationDegrees(rotation));
                            if (!(block instanceof StandingSignBlock)) pose.translate(0.0F, -0.3125F, -0.4375F);
                            pose.scale(scale, -scale, -scale);
                            model.root.render(pose, capture, 0, 0);
                        } else break;
                    } else if (block instanceof DecoratedPotBlock) {
                        if (!pots || potBaseMaterial == null || potSideMaterial == null) break;
                        TextureAtlasSprite base = sprite(potBaseMaterial), side = sprite(potSideMaterial);
                        if (base == null || side == null) break;
                        pose.translate(0.5, 0.0, 0.5);
                        pose.mulPose(Axis.YP.rotationDegrees(180.0F - state.getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot()));
                        pose.translate(-0.5, 0.0, -0.5);
                        for (String name : new String[] {"neck", "top", "bottom"}) potBase.getChild(name).render(pose, capture, 0, 0);
                        List<BakedQuad> quads = new ArrayList<>(capture.quads(base));
                        capture.begin();
                        for (String name : new String[] {"front", "back", "left", "right"}) potSides.getChild(name).render(pose, capture, 0, 0);
                        quads.addAll(capture.quads(side));
                        BakedModel original = vanilla.get(state);
                        if (original != null) out.put(state, new EbeModel(original, state, List.copyOf(quads), false));
                        continue;
                    } else if (block instanceof BellBlock) {
                        if (!bells) break;
                        material = BellRenderer.BELL_RESOURCE_LOCATION;
                        bellBody.resetPose();
                        bellBody.render(pose, capture, 0, 0);
                        keepVanilla = true;
                    } else break;
                    TextureAtlasSprite sprite = sprite(material);
                    if (sprite == null) break;
                    BakedModel original = vanilla.get(state);
                    List<BakedQuad> quads = capture.quads(sprite);
                    if (original != null && !quads.isEmpty()) out.put(state, new EbeModel(original, state, quads, keepVanilla));
                }
            }
        }

        private ModelPart part(ModelLayerLocation layer) { return layers.get(layer).bakeRoot(); }

        private @Nullable TextureAtlasSprite sprite(Material material) {
            TextureAtlasSprite sprite = atlas.getSprite(material.texture());
            if (sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation())) {
                LOG.warn("Baked block entities: {} is not in the block atlas, leaving it to its renderer", material.texture());
                return null;
            }
            return sprite;
        }
    }

    /** Records what an entity model draws: block-space positions and texture-space (0..1) UVs, four vertices per quad. */
    private static final class Capture implements VertexConsumer {
        private final List<float[]> vertices = new ArrayList<>();

        void begin() { vertices.clear(); }

        @Override
        public void vertex(float x, float y, float z, float r, float g, float b, float a, float u, float v, int overlay, int light,
                           float nx, float ny, float nz) {
            vertices.add(new float[] {x, y, z, u, v});
        }

        List<BakedQuad> quads(TextureAtlasSprite sprite) {
            List<BakedQuad> quads = new ArrayList<>(vertices.size() / 4);
            for (int q = 0; q + 3 < vertices.size(); q += 4) {
                float[] p = new float[12];
                for (int v = 0; v < 4; v++) System.arraycopy(vertices.get(q + v), 0, p, v * 3, 3);
                if (BakedQuadOrder.area2(p) < 1e-7f) continue;
                int direction = BakedQuadOrder.direction(p);
                int[] order = BakedQuadOrder.order(p, direction);
                int[] data = new int[32];
                for (int i = 0; i < 4; i++) {
                    float[] vertex = vertices.get(q + order[i]);
                    int at = i * 8; // vanilla FaceBakery layout: x y z color u v (light and normal left 0)
                    data[at] = Float.floatToRawIntBits(vertex[0]);
                    data[at + 1] = Float.floatToRawIntBits(vertex[1]);
                    data[at + 2] = Float.floatToRawIntBits(vertex[2]);
                    data[at + 3] = -1;
                    data[at + 4] = Float.floatToRawIntBits(sprite.getU(vertex[3] * 16.0));
                    data[at + 5] = Float.floatToRawIntBits(sprite.getV(vertex[4] * 16.0));
                }
                quads.add(new BakedQuad(data, -1, DIRECTIONS[direction], sprite, true));
            }
            return List.copyOf(quads);
        }

        // Model parts only use the bulk vertex(...) above.
        @Override public VertexConsumer vertex(double x, double y, double z) { return this; }
        @Override public VertexConsumer color(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer uv(float u, float v) { return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { return this; }
        @Override public VertexConsumer uv2(int u, int v) { return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { return this; }
        @Override public void endVertex() {}
        @Override public void defaultColor(int r, int g, int b, int a) {}
        @Override public void unsetDefaultColor() {}
    }

    /**
     * Baked block model: the vanilla model (particle texture, transforms; the bell's frame) plus the block entity's
     * quads, left out while the meshed position animates. All quads are unculled: none lies on a full block face.
     */
    static final class EbeModel implements BakedModel {
        private final BakedModel vanilla;
        private final List<BakedQuad> part;
        private final List<BakedQuad> vanillaUnculled;
        private final List<BakedQuad> withPart;
        private final boolean keepVanilla;

        EbeModel(BakedModel vanilla, BlockState state, List<BakedQuad> part, boolean keepVanilla) {
            this.vanilla = vanilla;
            this.part = part;
            this.keepVanilla = keepVanilla;
            this.vanillaUnculled = keepVanilla ? vanilla.getQuads(state, null, RandomSource.create(42L)) : List.of();
            List<BakedQuad> all = new ArrayList<>(vanillaUnculled);
            all.addAll(part);
            this.withPart = List.copyOf(all);
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
            if (side != null) return keepVanilla ? vanilla.getQuads(state, side, random) : List.of();
            long pos = MESH_POS.get()[0];
            boolean bake = pos == Long.MIN_VALUE ? !BakedBlockEntities.suspended() : BakedBlockEntities.bake(pos);
            if (keepVanilla) return bake ? withPart : vanillaUnculled;
            return bake ? part : List.of();
        }

        @Override public boolean useAmbientOcclusion() { return true; }
        @Override public boolean isGui3d() { return vanilla.isGui3d(); }
        @Override public boolean usesBlockLight() { return true; }
        @Override public boolean isCustomRenderer() { return false; }
        @Override public TextureAtlasSprite getParticleIcon() { return vanilla.getParticleIcon(); }
        @Override public ItemTransforms getTransforms() { return vanilla.getTransforms(); }
        @Override public ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }
    }

    /**
     * Resource packs can swap these models or textures through OptiFine-format CEM / random / emissive files (read by
     * Entity Model/Texture Features); a baked model would ignore them, so those types keep their renderer.
     */
    static final class PackOverrides {
        static final String[] CHESTS = {"chest", "trapped_chest", "ender_chest", "chest_large", "trapped_chest_large", "entity/chest/"};
        static final String[] SHULKERS = {"shulker_box", "entity/shulker/"};
        static final String[] BEDS = {"bed", "entity/bed/"};
        static final String[] SIGNS = {"sign", "hanging_sign", "entity/signs/"};
        static final String[] BELLS = {"bell", "entity/bell/"};
        static final String[] POTS = {"decorated_pot", "entity/decorated_pot/"};

        static boolean replaces(ResourceManager resources, String[] keys) {
            for (String key : keys) {
                if (key.endsWith("/")) {
                    String dir = key.substring(0, key.length() - 1);
                    if (!resources.listResources("optifine/random/" + dir, id -> true).isEmpty()
                        || !resources.listResources("textures/" + dir, id -> id.getPath().endsWith("_e.png")).isEmpty()) {
                        LOG.info("Baked block entities: resource pack random/emissive textures for {} found, keeping the vanilla renderer", dir);
                        return true;
                    }
                } else {
                    for (String root : new String[] {"optifine/cem/", "emf/cem/"}) {
                        if (resources.getResource(new ResourceLocation("minecraft", root + key + ".jem")).isPresent()) {
                            LOG.info("Baked block entities: resource pack model {}{}.jem found, keeping the vanilla renderer", root, key);
                            return true;
                        }
                    }
                }
            }
            return false;
        }
    }
}
