package com.thelads.core.v1_8_9.cit;

import com.thelads.core.client.ctm.CtmRules;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Connected Textures (CTM) for Minecraft 1.8.9 on Ionium (without OptiFine).
 * Reads CTM rules from resource packs and replaces block face textures
 * during chunk and block model meshing.
 */
public final class Ctm189 {
    private static final Logger LOGGER = LogManager.getLogger("TheLadsCore-1.8.9");

    private static final List<CtmRules.Rule> RULES = new ArrayList<CtmRules.Rule>();
    private static final Map<String, TextureAtlasSprite> SPRITES = new HashMap<String, TextureAtlasSprite>();
    private static boolean registered = false;

    private Ctm189() {}

    public static void register() {
        if (registered) return;
        registered = true;
        MinecraftForge.EVENT_BUS.register(new Ctm189());
    }

    @SubscribeEvent
    public void onTextureStitch(TextureStitchEvent.Pre event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.map != mc.getTextureMapBlocks()) return;

        RULES.clear();
        SPRITES.clear();

        reloadRules(mc.getResourceManager(), event.map);
    }

    public static void reloadRules(IResourceManager rm, TextureMap map) {
        RULES.clear();
        SPRITES.clear();

        // Scan known common CTM rule locations
        String[] defaultRules = {
                "optifine/ctm/glass/glass.properties",
                "mcpatcher/ctm/glass/glass.properties",
                "optifine/ctm/stained_glass/stained_glass.properties",
                "optifine/ctm/bookshelf/bookshelf.properties",
                "optifine/ctm/sandstone/sandstone.properties"
        };

        for (String rulePath : defaultRules) {
            ResourceLocation loc = new ResourceLocation("minecraft", rulePath);
            InputStream in = null;
            try {
                IResource res = rm.getResource(loc);
                in = res.getInputStream();
                String text = IOUtils.toString(new InputStreamReader(in, StandardCharsets.UTF_8));
                CtmRules.Rule rule = CtmRules.parseRule(rulePath, text);
                if (rule != null) {
                    RULES.add(rule);
                    if (map != null && rule.tiles != null) {
                        for (String tile : rule.tiles) {
                            String spriteName = "blocks/" + tile;
                            TextureAtlasSprite sprite = map.registerSprite(new ResourceLocation(spriteName));
                            SPRITES.put(tile, sprite);
                        }
                    }
                }
            } catch (Exception ignored) {
            } finally {
                IOUtils.closeQuietly(in);
            }
        }

        if (!RULES.isEmpty()) {
            LOGGER.info("Ctm189: loaded {} connected texture rules", RULES.size());
        }
    }

    public static TextureAtlasSprite getConnectedSprite(IBlockAccess world, IBlockState state, BlockPos pos,
                                                        EnumFacing face, TextureAtlasSprite original) {
        if (RULES.isEmpty() || world == null || state == null || pos == null || face == null) {
            return original;
        }

        Block block = state.getBlock();
        String blockName = Block.blockRegistry.getNameForObject(block).toString();

        for (CtmRules.Rule rule : RULES) {
            if (!rule.matchesFace(face.getName()) || !rule.matchesBlock(blockName)) {
                continue;
            }

            int index = 0;
            if (rule.method == CtmRules.Method.CTM) {
                index = evaluateCtmFace(world, block, pos, face);
            } else if (rule.method == CtmRules.Method.HORIZONTAL) {
                index = evaluateHorizontalFace(world, block, pos, face);
            } else if (rule.method == CtmRules.Method.REPEAT) {
                index = CtmRules.calculateRepeatIndex(pos.getX(), pos.getY(), rule.width, rule.height);
            }

            if (rule.tiles != null && index >= 0 && index < rule.tiles.length) {
                String tileName = rule.tiles[index];
                TextureAtlasSprite sprite = SPRITES.get(tileName);
                if (sprite != null) {
                    return sprite;
                }
            }
        }

        return original;
    }

    private static int evaluateCtmFace(IBlockAccess world, Block block, BlockPos pos, EnumFacing face) {
        EnumFacing u, d, l, r;
        if (face == EnumFacing.UP || face == EnumFacing.DOWN) {
            u = EnumFacing.NORTH; d = EnumFacing.SOUTH;
            l = EnumFacing.WEST;  r = EnumFacing.EAST;
        } else {
            u = EnumFacing.UP;    d = EnumFacing.DOWN;
            l = face.rotateYCCW(); r = face.rotateY();
        }

        boolean up = isSameBlock(world, block, pos.offset(u));
        boolean down = isSameBlock(world, block, pos.offset(d));
        boolean left = isSameBlock(world, block, pos.offset(l));
        boolean right = isSameBlock(world, block, pos.offset(r));

        boolean upLeft = isSameBlock(world, block, pos.offset(u).offset(l));
        boolean upRight = isSameBlock(world, block, pos.offset(u).offset(r));
        boolean downLeft = isSameBlock(world, block, pos.offset(d).offset(l));
        boolean downRight = isSameBlock(world, block, pos.offset(d).offset(r));

        return CtmRules.calculateCtmIndex(up, down, left, right, upLeft, upRight, downLeft, downRight);
    }

    private static int evaluateHorizontalFace(IBlockAccess world, Block block, BlockPos pos, EnumFacing face) {
        EnumFacing l = (face == EnumFacing.UP || face == EnumFacing.DOWN) ? EnumFacing.WEST : face.rotateYCCW();
        EnumFacing r = (face == EnumFacing.UP || face == EnumFacing.DOWN) ? EnumFacing.EAST : face.rotateY();

        boolean left = isSameBlock(world, block, pos.offset(l));
        boolean right = isSameBlock(world, block, pos.offset(r));

        return CtmRules.calculateHorizontalIndex(left, right);
    }

    private static boolean isSameBlock(IBlockAccess world, Block block, BlockPos pos) {
        return world.getBlockState(pos).getBlock() == block;
    }

    public static int getRuleCount() {
        return RULES.size();
    }
}
