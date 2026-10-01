// Derived from Optimized Cushions Backport 1.0.0 (commit a6de9cc) by NikitaCartes (MIT); see META-INF/lads-sources/cushions/LICENSE.
package com.thelads.core.v1_21_11.embedded.cushions;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.data.AtlasIds;

/**
 * Captures the backport cushion entity model geometry (with the exact PoseStack transforms
 * {@code CushionRenderer.submit} applies) once per model bake, and emits it into chunk
 * section buffers with entity-style lighting: a flat lightmap sampled at the entity's
 * light probe position and the fixed two-directional diffuse the entity shader would apply.
 */
public final class CushionBaker {
    private record QuadTemplate(Vector3f[] positions, float[] u, float[] v, Direction face) {
    }    private record CaptureSet(EntityModelSet source, Map<Direction, List<QuadTemplate>> byDirection) {
    }

    private static final ModelLayerLocation CUSHION_LAYER = new ModelLayerLocation(id("cushionbackport", "cushion"), "main");

    // Inlined from com.mojang.blaze3d.platform.Lighting: that class is client-only,
    // so no access widener is needed and a Mojang descriptor change cannot break boot.
    private static final Vector3fc DIFFUSE_LIGHT_0 = new Vector3f(0.2F, 1.0F, -0.7F).normalize();
    private static final Vector3fc DIFFUSE_LIGHT_1 = new Vector3f(-0.2F, 1.0F, 0.7F).normalize();
    private static final Vector3fc NETHER_DIFFUSE_LIGHT_0 = new Vector3f(0.2F, 1.0F, -0.7F).normalize();
    private static final Vector3fc NETHER_DIFFUSE_LIGHT_1 = new Vector3f(-0.2F, -1.0F, 0.7F).normalize();
    private static final float[] DIFFUSE_DEFAULT = diffuseByFace(DIFFUSE_LIGHT_0, DIFFUSE_LIGHT_1);
    private static final float[] DIFFUSE_NETHER = diffuseByFace(NETHER_DIFFUSE_LIGHT_0, NETHER_DIFFUSE_LIGHT_1);
    private static final EnumMap<DyeColor, Identifier> SPRITE_IDS = buildSpriteIds();

    private static final float UV_SCALE = 1.0F;

    private static volatile CaptureSet captured;

    private CushionBaker() {
    }

    private static EnumMap<DyeColor, Identifier> buildSpriteIds() {
        EnumMap<DyeColor, Identifier> ids = new EnumMap<>(DyeColor.class);
        for (DyeColor color : DyeColor.values()) {
            ids.put(color, id("cushionbackport", "entity/cushion/" + color.getName() + "_cushion"));
        }
        return ids;
    }

    private static Identifier id(final String namespace, final String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }

    private static TextureAtlasSprite sprite(final DyeColor color) {
        return Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).getSprite(SPRITE_IDS.get(color));
    }

    /** Called on section meshing worker threads (vanilla and Sodium alike: both regions expose block/sky brightness and cardinal lighting). */
    public static void emit(final VertexConsumer buffer, final CushionTracker.Snapshot cushion, final SectionPos sectionPos, final BlockAndTintGetter region) {
        List<QuadTemplate> quads;
        try {
            quads = templates(cushion.dir());
        } catch (Exception e) {
            return;
        }
        TextureAtlasSprite sprite;
        try {
            sprite = sprite(cushion.color());
        } catch (Exception e) {
            return;
        }
        float offsetX = (float)(cushion.x() - sectionPos.minBlockX());
        float offsetY = (float)(cushion.y() - sectionPos.minBlockY());
        float offsetZ = (float)(cushion.z() - sectionPos.minBlockZ());
        int light = LevelRenderer.getLightColor(region, cushion.lightPos());
        float[] diffuse = DIFFUSE_DEFAULT;

        for (QuadTemplate template : quads) {
            Direction face = template.face();
            int channel = (int)(diffuse[face.get3DDataValue()] * 255.0F) & 0xFF;
            int color = 0xFF000000 | (channel << 16) | (channel << 8) | channel;
            for (int i = 0; i < 4; i++) {
                Vector3f pos = template.positions()[i];
                emitVertex(buffer, offsetX + pos.x(), offsetY + pos.y(), offsetZ + pos.z(), color, sprite.getU(template.u()[i] * UV_SCALE), sprite.getV(template.v()[i] * UV_SCALE), light, face);
            }
        }
    }

    public static void emitFallback(final VertexConsumer buffer, final CushionTracker.Snapshot cushion, final SectionPos sectionPos, final BlockAndTintGetter region) {
        List<QuadTemplate> quads;
        // Same worker-thread isolation as emit above: one bad lookup must not abort the section.
        try {
            quads = templates(cushion.dir());
        } catch (Exception e) {
            return;
        }
        TextureAtlasSprite sprite;
        try {
            sprite = sprite(cushion.color());
        } catch (Exception e) {
            return;
        }
        float offsetX = (float)(cushion.x() - sectionPos.minBlockX());
        float offsetY = (float)(cushion.y() - sectionPos.minBlockY());
        float offsetZ = (float)(cushion.z() - sectionPos.minBlockZ());
        int light = LevelRenderer.getLightColor(region, cushion.lightPos());
        float[] diffuse = DIFFUSE_DEFAULT;
        for (QuadTemplate template : quads) {
            Direction face = template.face();
            int channel = (int)(diffuse[face.get3DDataValue()] * 255.0F) & 0xFF;
            int color = 0xFF000000 | (channel << 16) | (channel << 8) | channel;
            for (int i = 0; i < 4; i++) {
                Vector3f pos = template.positions()[i];
                buffer.addVertex(offsetX + pos.x(), offsetY + pos.y(), offsetZ + pos.z())
                    .setColor(color)
                    .setUv(sprite.getU(template.u()[i] * UV_SCALE), sprite.getV(template.v()[i] * UV_SCALE))
                    .setLight(light)
                    .setNormal((float)face.getStepX(), (float)face.getStepY(), (float)face.getStepZ());
            }
        }
    }

    private static void emitVertex(final VertexConsumer buffer, final float x, final float y, final float z, final int color, final float u, final float v, final int light, final Direction face) {
        buffer.addVertex(x, y, z, color, u, v, OverlayTexture.NO_OVERLAY, light, face.getStepX(), face.getStepY(), face.getStepZ());
    }

    private static List<QuadTemplate> templates(final Direction direction) {
        EntityModelSet models = Minecraft.getInstance().getEntityModels();
        CaptureSet set = captured;
        if (set == null || set.source() != models) {
            synchronized (CushionBaker.class) {
                set = captured;
                if (set == null || set.source() != models) {
                    set = new CaptureSet(models, capture(models));
                    captured = set;
                }
            }
        }

        return set.byDirection().get(direction);
    }

    private static Map<Direction, List<QuadTemplate>> capture(final EntityModelSet models) {
        ModelPart root = models.bakeLayer(CUSHION_LAYER);
        Map<Direction, List<QuadTemplate>> byDirection = new EnumMap<>(Direction.class);

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            List<QuadTemplate> quads = new ArrayList<>();
            PoseStack poseStack = poseFor(direction);
            root.visit(poseStack, (pose, path, cubeIndex, cube) -> {
                for (ModelPart.Polygon polygon : cube.polygons) {
                    if (polygon.vertices().length != 4) {
                        continue;
                    }

                    Vector3f normal = pose.transformNormal(polygon.normal(), new Vector3f());
                    Direction face = Direction.getApproximateNearest(normal.x(), normal.y(), normal.z());
                    Vector3f[] positions = new Vector3f[4];
                    float[] u = new float[4];
                    float[] v = new float[4];

                    for (int i = 0; i < 4; i++) {
                        ModelPart.Vertex vertex = polygon.vertices()[i];
                        positions[i] = pose.pose().transformPosition(vertex.worldX(), vertex.worldY(), vertex.worldZ(), new Vector3f());
                        u[i] = vertex.u();
                        v[i] = vertex.v();
                    }

                    quads.add(new QuadTemplate(positions, u, v, face));
                }
            });
            byDirection.put(direction, quads);
        }

        return byDirection;
    }

    /** Replays the transforms of the backport CushionRenderer.submit for each horizontal facing. */
    private static PoseStack poseFor(final Direction direction) {
        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(Axis.YP.rotationDegrees(direction.toYRot()));
        poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
        poseStack.translate(0.0F, -0.25F, 0.0F);
        return poseStack;
    }

    /** Entity shader diffuse: min(1, 0.4 + 0.6 * (max(0, L0·N) + max(0, L1·N))) per axis face. */
    private static float[] diffuseByFace(final Vector3fc light0, final Vector3fc light1) {
        float[] byFace = new float[6];
        for (Direction direction : Direction.values()) {
            Vector3f normal = new Vector3f(direction.getUnitVec3f());
            float accum = Math.max(0.0F, light0.dot(normal)) + Math.max(0.0F, light1.dot(normal));
            byFace[direction.get3DDataValue()] = Math.min(1.0F, 0.4F + 0.6F * accum);
        }

        return byFace;
    }
}
