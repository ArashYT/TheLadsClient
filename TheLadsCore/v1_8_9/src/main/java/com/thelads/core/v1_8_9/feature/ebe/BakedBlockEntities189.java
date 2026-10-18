package com.thelads.core.v1_8_9.feature.ebe;

import com.thelads.core.client.ebe.BakedBlockEntities;
import com.thelads.core.client.ebe.BakedQuadOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.block.BlockSign;
import net.minecraft.block.BlockStandingSign;
import net.minecraft.block.BlockWallSign;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelChest;
import net.minecraft.client.model.ModelLargeChest;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.model.ModelSign;
import net.minecraft.client.model.PositionTextureVertex;
import net.minecraft.client.model.TexturedQuad;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.IResourceManager;
import java.awt.image.BufferedImage;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityEnderChest;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumWorldBlockLayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 1.8.9 side of {@link BakedBlockEntities}: chests (normal, trapped, Christmas; single and double), ender chests and the
 * boards of signs become baked block models (the user's 1.8.9 scope; beds are block models there already). Geometry and
 * UVs come from vanilla's own ModelChest / ModelLargeChest / ModelSign through the same transforms the tile entity
 * renderers apply; the textures are stitched into the block atlas. {@code BlockRendererDispatcher.renderBlock} receives
 * the position, so the baked model is chosen there; sign text keeps being drawn by its renderer.
 */
public final class BakedBlockEntities189 {
    private static final Logger LOG = LoggerFactory.getLogger("TheLadsCore/BakedBlockEntities");
    private static final String[] TEXTURES = {"normal", "normal_double", "trapped", "trapped_double", "christmas", "christmas_double", "ender"};
    private static final float SCALE = 0.0625F;

    /** [variant][facing index 2..5 or sign rotation 0..15] */
    private static volatile IBakedModel[][] chests = new IBakedModel[0][];
    private static volatile IBakedModel[] standingSigns = new IBakedModel[0], wallSigns = new IBakedModel[0];
    private static volatile boolean christmas;
    private static boolean initialised;
    /** Main thread, sign renderer: the board of the sign being rendered is in the chunk mesh (only its text is drawn). */
    public static boolean signBoardBaked;

    private BakedBlockEntities189() {}

    /** Client entrypoint. */
    public static synchronized void init() {
        if (initialised) return;
        initialised = true;
        if (!BakedBlockEntities.init()) {
            LOG.info("Baked block entities standing down: {}", BakedBlockEntities.standDownReason());
            return;
        }
        MinecraftForge.EVENT_BUS.register(new BakedBlockEntities189());
        LOG.info("Baked block entities on (chests, ender chests, signs)");
    }

    static boolean baking() { return chests.length > 0 && BakedBlockEntities.active(); }

    @SubscribeEvent
    public void stitchPre(TextureStitchEvent.Pre event) {
        if (event.map != Minecraft.getMinecraft().getTextureMapBlocks()) return; // Essential and OptiFine stitch atlases of their own
        // OptiFine's shader texture loader (ShadersTex.genMipmapsSimple) mips only square sprites: the 128x64 double chests and the
        // 64x32 sign threw on the first stitch with a shader pack on, and Forge then dropped every resource pack (1.9.12). With
        // shaders on, chests and signs stay vanilla block entities.
        if (com.thelads.core.v1_8_9.feature.RenderScale189.shaders()) return;
        for (String name : TEXTURES) event.map.setTextureEntry("minecraft:entity/chest/" + name, new EntitySprite("minecraft:entity/chest/" + name));
        event.map.setTextureEntry("minecraft:entity/sign", new EntitySprite("minecraft:entity/sign"));
    }

    @SubscribeEvent
    public void stitchPost(TextureStitchEvent.Post event) {
        if (event.map != Minecraft.getMinecraft().getTextureMapBlocks()) return;
        BakedBlockEntities.clear();
        if (com.thelads.core.v1_8_9.feature.RenderScale189.shaders()) { // nothing was stitched for them (stitchPre)
            chests = new IBakedModel[0][];
            standingSigns = wallSigns = new IBakedModel[0];
            return;
        }
        try {
            bake(event.map);
        } catch (Throwable t) {
            LOG.error("Baked block entities disabled for this reload", t);
            chests = new IBakedModel[0][];
            standingSigns = wallSigns = new IBakedModel[0];
        }
    }

    // ---- baking (texture reload) -----------------------------------------------------------------------------------

    /** Chest variants: 0 normal, 1 trapped, 2 Christmas, 3 ender; +4 for the double chest along X, +8 along Z. */
    private static void bake(TextureMap map) {
        TextureAtlasSprite missing = map.getMissingSprite();
        TextureAtlasSprite[] sprites = new TextureAtlasSprite[TEXTURES.length];
        for (int i = 0; i < TEXTURES.length; i++) sprites[i] = map.getAtlasSprite("minecraft:entity/chest/" + TEXTURES[i]);
        TextureAtlasSprite signSprite = map.getAtlasSprite("minecraft:entity/sign");
        if (Arrays.asList(sprites).contains(missing) || signSprite == missing) {
            // The game's first block-atlas pass skips loading sprites (the model reload right after stitches for real).
            LOG.info("Baked block entities: block atlas without the entity textures this pass, renderers stay in use");
            chests = new IBakedModel[0][];
            return;
        }
        TextureAtlasSprite planks = map.getAtlasSprite("minecraft:blocks/planks_oak"), obsidian = map.getAtlasSprite("minecraft:blocks/obsidian");

        ModelChest single = new ModelChest();
        ModelChest large = new ModelLargeChest();
        single.chestLid.rotateAngleX = single.chestKnob.rotateAngleX = 0F;
        large.chestLid.rotateAngleX = large.chestKnob.rotateAngleX = 0F;
        IBakedModel[][] chestModels = new IBakedModel[12][6];
        for (int variant = 0; variant < 12; variant++) {
            int kind = variant & 3;
            int doubled = variant >> 2; // 0 single, 1 double along X, 2 double along Z
            if (kind == 3 && doubled != 0) continue; // ender chests never pair
            int texture = kind == 3 ? 6 : kind * 2 + (doubled != 0 ? 1 : 0);
            for (int facing = 2; facing <= 5; facing++) {
                if (doubled == 1 && facing > 3 || doubled == 2 && facing < 4) continue; // pairs along X face north/south
                Matrix4f m = new Matrix4f();
                m.translate(new Vector3f(0F, 1F, 1F));
                m.scale(new Vector3f(1F, -1F, -1F));
                m.translate(new Vector3f(0.5F, 0.5F, 0.5F));
                if (facing == 2 && doubled == 1) m.translate(new Vector3f(1F, 0F, 0F));
                if (facing == 5 && doubled == 2) m.translate(new Vector3f(0F, 0F, -1F));
                m.rotate((float) Math.toRadians(facing == 2 ? 180 : facing == 4 ? 90 : facing == 5 ? -90 : 0), new Vector3f(0F, 1F, 0F));
                m.translate(new Vector3f(-0.5F, -0.5F, -0.5F));
                ModelChest model = doubled != 0 ? large : single;
                List<float[]> quads = new ArrayList<float[]>();
                capture(model.chestLid, m, quads);
                capture(model.chestKnob, m, quads);
                capture(model.chestBelow, m, quads);
                chestModels[variant][facing] = new Model(bakeQuads(quads, sprites[texture]), kind == 3 ? obsidian : planks);
            }
        }
        ModelSign sign = new ModelSign();
        IBakedModel[] standing = new IBakedModel[16], wall = new IBakedModel[6];
        for (int rotation = 0; rotation < 16; rotation++) standing[rotation] = signModel(sign, true, rotation * 360 / 16.0F, signSprite, planks);
        for (int facing = 2; facing <= 5; facing++)
            wall[facing] = signModel(sign, false, facing == 2 ? 180F : facing == 4 ? 90F : facing == 5 ? -90F : 0F, signSprite, planks);
        christmas = BakedBlockEntities.christmas(); // vanilla's chest renderer also reads the date once
        chests = chestModels;
        standingSigns = standing;
        wallSigns = wall;
        LOG.info("Baked block entities: chest, ender chest and sign models baked");
    }

    private static IBakedModel signModel(ModelSign sign, boolean standing, float degrees, TextureAtlasSprite sprite, TextureAtlasSprite particle) {
        float f = 0.6666667F;
        Matrix4f m = new Matrix4f();
        m.translate(new Vector3f(0.5F, 0.75F * f, 0.5F));
        m.rotate((float) Math.toRadians(-degrees), new Vector3f(0F, 1F, 0F));
        if (!standing) m.translate(new Vector3f(0F, -0.3125F, -0.4375F));
        m.scale(new Vector3f(f, -f, -f));
        List<float[]> quads = new ArrayList<float[]>();
        capture(sign.signBoard, m, quads);
        if (standing) capture(sign.signStick, m, quads);
        return new Model(bakeQuads(quads, sprite), particle);
    }

    /** Software copy of ModelRenderer.render(0.0625F): its GL matrix ops applied to every box quad. */
    private static void capture(ModelRenderer part, Matrix4f parent, List<float[]> out) {
        if (part.isHidden || !part.showModel) return;
        Matrix4f m = Matrix4f.mul(parent, new Matrix4f(), null);
        m.translate(new Vector3f(part.offsetX, part.offsetY, part.offsetZ));
        m.translate(new Vector3f(part.rotationPointX * SCALE, part.rotationPointY * SCALE, part.rotationPointZ * SCALE));
        if (part.rotateAngleZ != 0F) m.rotate(part.rotateAngleZ, new Vector3f(0F, 0F, 1F));
        if (part.rotateAngleY != 0F) m.rotate(part.rotateAngleY, new Vector3f(0F, 1F, 0F));
        if (part.rotateAngleX != 0F) m.rotate(part.rotateAngleX, new Vector3f(1F, 0F, 0F));
        for (ModelBox box : part.cubeList) {
            for (TexturedQuad quad : ((ModelBoxQuads) box).lads$quads()) {
                float[] q = new float[20]; // x y z * 4, then u v * 4
                for (int v = 0; v < 4; v++) {
                    PositionTextureVertex vertex = quad.vertexPositions[v];
                    Vector4f p = Matrix4f.transform(m, new Vector4f((float) vertex.vector3D.xCoord * SCALE,
                        (float) vertex.vector3D.yCoord * SCALE, (float) vertex.vector3D.zCoord * SCALE, 1F), null);
                    q[v * 3] = p.x;
                    q[v * 3 + 1] = p.y;
                    q[v * 3 + 2] = p.z;
                    q[12 + v * 2] = vertex.texturePositionX;
                    q[13 + v * 2] = vertex.texturePositionY;
                }
                out.add(q);
            }
        }
        if (part.childModels != null) for (ModelRenderer child : part.childModels) capture(child, m, out);
    }

    private static final float[] SHADE = {0.5F, 1.0F, 0.8F, 0.8F, 0.6F, 0.6F};

    private static List<BakedQuad> bakeQuads(List<float[]> captured, TextureAtlasSprite sprite) {
        List<BakedQuad> quads = new ArrayList<BakedQuad>();
        for (float[] q : captured) {
            float[] p = new float[12];
            System.arraycopy(q, 0, p, 0, 12);
            if (BakedQuadOrder.area2(p) < 1e-7F) continue;
            int direction = BakedQuadOrder.direction(p);
            int[] order = BakedQuadOrder.order(p, direction);
            int shade = Math.min(255, Math.max(0, (int) (SHADE[direction] * 255.0F)));
            int[] data = new int[28];
            for (int i = 0; i < 4; i++) {
                int v = order[i], at = i * 7; // vanilla FaceBakery layout: x y z shadeColor u v (lightmap left 0)
                data[at] = Float.floatToRawIntBits(p[v * 3]);
                data[at + 1] = Float.floatToRawIntBits(p[v * 3 + 1]);
                data[at + 2] = Float.floatToRawIntBits(p[v * 3 + 2]);
                data[at + 3] = 0xFF000000 | shade << 16 | shade << 8 | shade;
                data[at + 4] = Float.floatToRawIntBits(sprite.getInterpolatedU(q[12 + v * 2] * 16.0));
                data[at + 5] = Float.floatToRawIntBits(sprite.getInterpolatedV(q[13 + v * 2] * 16.0));
            }
            quads.add(new BakedQuad(data, -1, EnumFacing.VALUES[direction]));
        }
        return Collections.unmodifiableList(quads);
    }

    /**
     * An entity texture in the block atlas. 1.8.9's sprite loader takes only square images (or animation strips); the
     * double chest (128x64) and the sign (64x32) are not, so they are read here, as they are, without that check.
     */
    static final class EntitySprite extends TextureAtlasSprite {
        EntitySprite(String name) { super(name); }

        @Override
        public boolean hasCustomLoader(IResourceManager manager, ResourceLocation location) { return true; }

        @Override
        public boolean load(IResourceManager manager, ResourceLocation location) {
            ResourceLocation file = new ResourceLocation(location.getResourceDomain(), "textures/" + location.getResourcePath() + ".png");
            try {
                BufferedImage image = TextureUtil.readBufferedImage(manager.getResource(file).getInputStream());
                width = image.getWidth();
                height = image.getHeight();
                int[] pixels = new int[width * height];
                image.getRGB(0, 0, width, height, pixels, 0, width);
                framesTextureData.clear();
                int[][] levels = new int[5][]; // as vanilla: one slot per mip level (at most 4), filled by generateMipmaps
                levels[0] = pixels;
                framesTextureData.add(levels);
                return false; // loaded: stitch it (mipmaps are generated from frame 0 like any sprite)
            } catch (Exception e) {
                LOG.warn("Baked block entities: cannot read {}", file, e);
                return true;
            }
        }
    }

    /** Added to ModelBox by mixin: its (private) quads. */
    public interface ModelBoxQuads { TexturedQuad[] lads$quads(); }

    // ---- chunk meshing (any thread) --------------------------------------------------------------------------------

    /**
     * BlockRendererDispatcher.renderBlock: the baked model to mesh for this block, or null for vanilla's path (dynamic,
     * suspended or not a baked type). {@link #NOTHING} for the second half of a double chest (the first half draws both).
     */
    public static IBakedModel model(IBlockState state, BlockPos pos, IBlockAccess world) {
        IBakedModel[][] chestModels = chests;
        if (chestModels.length == 0) return null;
        Block block = state.getBlock();
        IBakedModel model;
        if (block == Blocks.chest || block == Blocks.trapped_chest) {
            if (world.getBlockState(pos.west()).getBlock() == block || world.getBlockState(pos.north()).getBlock() == block) return NOTHING;
            int kind = christmas ? 2 : block == Blocks.trapped_chest ? 1 : 0;
            int facing = state.getValue(BlockChest.FACING).getIndex();
            int doubled = world.getBlockState(pos.east()).getBlock() == block ? 1 : world.getBlockState(pos.south()).getBlock() == block ? 2 : 0;
            if (doubled == 1 && facing > 3) facing = facing == 4 ? 2 : 3; // a pair along X always faces north/south
            if (doubled == 2 && facing < 4) facing = facing == 2 ? 4 : 5;
            model = chestModels[kind + 4 * doubled][facing];
        } else if (block == Blocks.ender_chest) {
            model = chestModels[3][state.getValue(BlockChest.FACING).getIndex()];
        } else if (block == Blocks.standing_sign) {
            model = standingSigns[state.getValue(BlockStandingSign.ROTATION)];
        } else if (block == Blocks.wall_sign) {
            model = wallSigns[state.getValue(BlockWallSign.FACING).getIndex()];
        } else return null;
        return model != null && BakedBlockEntities.bake(pos.toLong()) ? model : null;
    }

    public static final IBakedModel NOTHING = new Model(Collections.<BakedQuad>emptyList(), null);

    /** Signs report render type 2 (iterated by the chunk builder, drawn by renderBlock) while baking runs. */
    public static boolean meshedSign(Block block) { return block instanceof BlockSign && baking(); }

    /** Cutout, as the tile entity renderers draw them: alpha-tested and without mipmaps. */
    public static EnumWorldBlockLayer layer(Block block) {
        if (!baking()) return null;
        return block == Blocks.chest || block == Blocks.trapped_chest || block == Blocks.ender_chest || block instanceof BlockSign
            ? EnumWorldBlockLayer.CUTOUT : null;
    }

    // ---- tile entity render path (main thread) ---------------------------------------------------------------------

    /** TileEntityRendererDispatcher.renderTileEntityAt: true when the chunk mesh shows it, so its renderer is skipped. */
    public static boolean skipRenderer(TileEntity entity, int destroyStage) {
        if (destroyStage >= 0 || !baking() || entity instanceof TileEntitySign) return false; // breaking cracks draw through it
        if (!(entity instanceof TileEntityChest) && !(entity instanceof TileEntityEnderChest)) return false;
        // Only the client world's chunk meshes hold baked models: the item renderer's world-less chests (held, in the inventory,
        // dropped, in item frames, chest minecarts, menus without a world) at 0,0,0 were skipped as if baked, so every chest item was invisible.
        if (entity.getWorld() == null || entity.getWorld() != Minecraft.getMinecraft().theWorld) return false;
        BlockPos pos = entity.getPos();
        long key = pos.toLong();
        if (BakedBlockEntities.setAnimating(key, animating(entity))) {
            Minecraft.getMinecraft().renderGlobal.markBlockRangeForRenderUpdate(pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ());
        }
        return !BakedBlockEntities.drawDynamic(key);
    }

    /** TileEntitySignRenderer: is this sign's board in the chunk mesh (the renderer then draws only the text)? */
    public static boolean signBoardBaked(TileEntity sign, int destroyStage) {
        return destroyStage < 0 && baking() && !BakedBlockEntities.drawDynamic(sign.getPos().toLong());
    }

    @SubscribeEvent
    public void worldUnload(net.minecraftforge.event.world.WorldEvent.Unload event) {
        if (event.world != null && event.world.isRemote) {
            BakedBlockEntities.clear();
        }
    }

    private static boolean animating(TileEntity entity) {
        if (entity instanceof TileEntityEnderChest) {
            TileEntityEnderChest ender = (TileEntityEnderChest) entity;
            return ender.lidAngle > 0F || ender.prevLidAngle > 0F || ender.numPlayersUsing > 0;
        }
        TileEntityChest chest = (TileEntityChest) entity;
        if (moving(chest)) return true;
        if (!chest.adjacentChestChecked) {
            chest.checkForAdjacentChests();
        }
        if (chest.adjacentChestZNeg != null && moving(chest.adjacentChestZNeg)) return true;
        if (chest.adjacentChestXPos != null && moving(chest.adjacentChestXPos)) return true;
        if (chest.adjacentChestXNeg != null && moving(chest.adjacentChestXNeg)) return true;
        if (chest.adjacentChestZPos != null && moving(chest.adjacentChestZPos)) return true;
        return false;
    }

    private static boolean moving(TileEntityChest chest) {
        return chest.lidAngle > 0F || chest.prevLidAngle > 0F || chest.numPlayersUsing > 0;
    }

    /** QA probe only: hold the lid of a (ender) chest at {@code angle} (0..1). */
    static void force(TileEntity entity, float angle) {
        if (entity instanceof TileEntityChest) {
            TileEntityChest chest = (TileEntityChest) entity;
            chest.lidAngle = chest.prevLidAngle = angle;
            chest.numPlayersUsing = angle > 0F ? 1 : 0;
        } else if (entity instanceof TileEntityEnderChest) {
            TileEntityEnderChest ender = (TileEntityEnderChest) entity;
            ender.lidAngle = ender.prevLidAngle = angle;
            ender.numPlayersUsing = angle > 0F ? 1 : 0;
        }
    }

    /** Baked block model: general (unculled) quads only; none lies on a full block face. */
    static final class Model implements IBakedModel {
        private final List<BakedQuad> quads;
        private final TextureAtlasSprite particle;

        Model(List<BakedQuad> quads, TextureAtlasSprite particle) {
            this.quads = quads;
            this.particle = particle;
        }

        @Override public List<BakedQuad> getFaceQuads(EnumFacing facing) { return Collections.emptyList(); }
        @Override public List<BakedQuad> getGeneralQuads() { return quads; }
        @Override public boolean isAmbientOcclusion() { return true; }
        @Override public boolean isGui3d() { return false; }
        @Override public boolean isBuiltInRenderer() { return false; }
        @Override public TextureAtlasSprite getParticleTexture() { return particle; }
        @Override public ItemCameraTransforms getItemCameraTransforms() { return ItemCameraTransforms.DEFAULT; }
    }
}
