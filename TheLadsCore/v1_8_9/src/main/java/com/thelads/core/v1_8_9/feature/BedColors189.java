package com.thelads.core.v1_8_9.feature;

import com.thelads.core.modules.BedColorsModule;
import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockCarpet;
import net.minecraft.block.BlockColored;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public final class BedColors189 {
    private static final Random RNG = new Random();

    public static final class BedRenderContext {
        private final BlockPos pos;
        private final IBlockAccess world;

        public BedRenderContext(BlockPos pos, IBlockAccess world) {
            this.pos = pos;
            this.world = world;
        }

        public BlockPos getPos() {
            return pos;
        }

        public IBlockAccess getWorld() {
            return world;
        }
    }

    private static final ThreadLocal<BedRenderContext> CURRENT_CONTEXT = new ThreadLocal<>();

    private BedColors189() {}

    public static boolean isRenderingBed() {
        return CURRENT_CONTEXT.get() != null && BedColorsModule.isActive();
    }

    public static void setRenderingBed(boolean rendering, BlockPos pos, IBlockAccess world) {
        if (rendering) {
            CURRENT_CONTEXT.set(new BedRenderContext(pos, world));
        } else {
            CURRENT_CONTEXT.remove();
        }
    }

    public static BedRenderContext getCurrentContext() {
        return CURRENT_CONTEXT.get();
    }

    /**
     * Determine bed color based on Bed Wars team blocks (wool, clay, carpet) around the bed.
     * Returns RGB hex (e.g. 0xE74C3C), or -1 if default/disabled.
     */
    public static int getBedColor(IBlockAccess world, BlockPos pos) {
        if (!BedColorsModule.isActive()) return -1;
        BedColorsModule mod = BedColorsModule.get();
        if (mod == null) return -1;
        if (!mod.teamColors.get()) {
            return mod.customColor.getColor() & 0xFFFFFF;
        }
        if (world == null || pos == null) {
            return mod.customColor.getColor() & 0xFFFFFF;
        }

        // 1. Check block directly underneath
        int color = detectBlockColor(world, pos.down());
        if (color != -1) return color;

        // 2. Check surrounding neighbor blocks within base
        for (EnumFacing face : EnumFacing.HORIZONTALS) {
            color = detectBlockColor(world, pos.offset(face));
            if (color != -1) return color;
            color = detectBlockColor(world, pos.down().offset(face));
            if (color != -1) return color;
        }

        return mod.customColor.getColor() & 0xFFFFFF;
    }

    private static int detectBlockColor(IBlockAccess world, BlockPos pos) {
        if (pos.getY() < 0 || pos.getY() >= 256) return -1;
        IBlockState state = world.getBlockState(pos);
        Block b = state.getBlock();
        if (b == Blocks.wool) {
            EnumDyeColor dye = state.getValue(BlockColored.COLOR);
            return getTeamColorRgb(dye);
        }
        if (b == Blocks.stained_hardened_clay) {
            EnumDyeColor dye = state.getValue(BlockColored.COLOR);
            return getTeamColorRgb(dye);
        }
        if (b == Blocks.carpet) {
            EnumDyeColor dye = state.getValue(BlockCarpet.COLOR);
            return getTeamColorRgb(dye);
        }
        return -1;
    }

    public static int getTeamColorRgb(EnumDyeColor dye) {
        if (dye == null) return 0xE74C3C;
        switch (dye) {
            case RED: return 0xE74C3C;
            case BLUE: return 0x3498DB;
            case GREEN:
            case LIME: return 0x2ECC71;
            case YELLOW: return 0xF1C40F;
            case CYAN:
            case LIGHT_BLUE: return 0x00FFFF;
            case WHITE: return 0xFFFFFF;
            case PINK:
            case MAGENTA: return 0xFF69B4;
            case GRAY: return 0x808080;
            case SILVER: return 0xAAAAAA;
            case ORANGE: return 0xE67E22;
            case PURPLE: return 0x9B59B6;
            case BROWN: return 0x795548;
            case BLACK: return 0x2C3E50;
            default: return 0xE74C3C;
        }
    }

    public static void spawnBedDestructionBurst(BlockPos pos, IBlockState state) {
        if (!BedColorsModule.isActive()) return;
        BedColorsModule mod = BedColorsModule.get();
        if (mod == null || !mod.destructionVisuals.get()) return;
        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.theWorld;
        if (world == null) return;

        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;

        int bedColor = getBedColor(world, pos);
        if (bedColor == -1) bedColor = 0xE74C3C;
        float r = ((bedColor >> 16) & 0xFF) / 255.0f;
        float g = ((bedColor >> 8) & 0xFF) / 255.0f;
        float b = (bedColor & 0xFF) / 255.0f;

        int count = mod.particleCount.getIntValue();
        for (int i = 0; i < count; i++) {
            double vx = (RNG.nextDouble() - 0.5) * 0.4;
            double vy = RNG.nextDouble() * 0.3 + 0.1;
            double vz = (RNG.nextDouble() - 0.5) * 0.4;
            world.spawnParticle(EnumParticleTypes.FIREWORKS_SPARK, cx, cy, cz, vx, vy, vz);
            world.spawnParticle(EnumParticleTypes.CRIT_MAGIC, cx, cy, cz, vx * 0.8, vy * 0.8, vz * 0.8);
            world.spawnParticle(EnumParticleTypes.REDSTONE, cx + (RNG.nextDouble() - 0.5) * 0.6, cy + (RNG.nextDouble() - 0.5) * 0.4, cz + (RNG.nextDouble() - 0.5) * 0.6, r == 0 ? 0.001f : r, g, b);
        }
        world.spawnParticle(EnumParticleTypes.EXPLOSION_NORMAL, cx, cy, cz, 0.0, 0.05, 0.0);
    }
}
