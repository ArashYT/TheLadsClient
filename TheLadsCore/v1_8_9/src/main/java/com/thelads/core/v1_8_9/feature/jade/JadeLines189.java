package com.thelads.core.v1_8_9.feature.jade;

import com.thelads.core.client.jade.JadeMath;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.block.BlockCauldron;
import net.minecraft.block.BlockCocoa;
import net.minecraft.block.BlockCrops;
import net.minecraft.block.BlockLever;
import net.minecraft.block.BlockNetherWart;
import net.minecraft.block.BlockNote;
import net.minecraft.block.BlockRedstoneComparator;
import net.minecraft.block.BlockRedstoneRepeater;
import net.minecraft.block.BlockStem;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityAgeable;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IEntityOwnable;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityBanner;
import net.minecraft.tileentity.TileEntityMobSpawner;
import net.minecraft.tileentity.TileEntityNote;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.registry.EntityRegistry;

/** Builds the lines of the Jade panel from what the 1.8.9 client knows about the target (JadeLines1201's port). */
final class JadeLines189 {
    static final int TEXT = 0, HEARTS = 1, ARMOR = 2;
    static final int PAD = 4, LINE = 10;
    /** TileEntityNote.triggerNote: the material under the note block picks the instrument. */
    private static final String[] INSTRUMENTS = {"Harp", "Basedrum", "Snare", "Hat", "Bass"};
    private static final String GRAY = EnumChatFormatting.GRAY.toString();
    private static final Map<String, String> MOD_NAMES = new HashMap<>();
    private static volatile BlockPos notePos;
    private static volatile int note = -1;

    private JadeLines189() {}

    /** One drawable row. TEXT uses {@code text}; HEARTS and ARMOR use {@code value}/{@code max}. */
    static final class Line {
        final int kind, width, height;
        final String text;
        final float value, max;

        Line(int kind, String text, float value, float max, int width, int height) {
            this.kind = kind;
            this.text = text;
            this.value = value;
            this.max = max;
            this.width = width;
            this.height = height;
        }

        String plain() {
            if (kind == HEARTS) return "Health: " + JadeMath.number(value) + "/" + JadeMath.number(max);
            if (kind == ARMOR) return "Armor: " + (int) value;
            return EnumChatFormatting.getTextWithoutFormattingCodes(text);
        }
    }

    /** What the panel shows: built every client tick, drawn every frame. {@code icon} may be null. */
    static final class Panel {
        static final Panel EMPTY = new Panel(null, Collections.<Line>emptyList(), 0, 0);
        final ItemStack icon;
        final List<Line> lines;
        final int width, height;

        Panel(ItemStack icon, List<Line> lines, int width, int height) {
            this.icon = icon;
            this.lines = lines;
            this.width = width;
            this.height = height;
        }

        List<String> plain() {
            List<String> out = new ArrayList<>(lines.size());
            for (Line line : lines) out.add(line.plain());
            return out;
        }
    }

    private static final class Out {
        final FontRenderer font;
        final List<Line> lines = new ArrayList<>();

        Out(FontRenderer font) { this.font = font; }

        void text(String text) { lines.add(new Line(TEXT, text, 0, 0, font.getStringWidth(text), LINE)); }

        void gray(String text) { text(GRAY + text); }

        void hearts(float health, float max) {
            int containers = Math.max(1, (int) Math.ceil(max / 2));
            lines.add(new Line(HEARTS, null, health, max, Math.min(10, containers) * 8 + 1, (containers + 9) / 10 * LINE));
        }

        void armor(int value) { lines.add(new Line(ARMOR, null, value, 20, (value + 1) / 2 * 8 + 1, LINE)); }

        Panel finish(ItemStack icon, Jade189.Config config) {
            boolean showIcon = config.icon && icon != null;
            int textWidth = 0, textHeight = 0;
            for (Line line : lines) {
                textWidth = Math.max(textWidth, line.width);
                textHeight += line.height;
            }
            int width = PAD * 2 + (showIcon ? 20 : 0) + textWidth;
            int height = PAD * 2 + Math.max(textHeight - 1, showIcon ? 16 : 0);
            return new Panel(showIcon ? icon : null, lines, width, height);
        }
    }

    // ---- Blocks ----

    static Panel block(Minecraft mc, MovingObjectPosition hit, boolean fluid, Jade189.Config config) {
        World world = mc.theWorld;
        BlockPos pos = hit.getBlockPos();
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        try {
            state = block.getActualState(state, world, pos); // a repeater's lock is only in its actual state
        } catch (RuntimeException ignored) {
            // A modded block that needs more than the client world: keep the stored state.
        }
        TileEntity tile = fluid ? null : world.getTileEntity(pos);
        ItemStack icon = fluid ? bucket(block) : pick(block, hit, world, pos, mc.thePlayer);
        Out out = new Out(mc.fontRendererObj);
        // The picked stack names metadata variants ("Red Wool"); crops and the like pick another item (seeds), so they keep the block's.
        out.text(icon != null && icon.getItem() == Item.getItemFromBlock(block) ? icon.getDisplayName() : block.getLocalizedName());

        if (config.growth) growth(out, block, state);
        if (config.harvest && !fluid) harvest(out, mc.thePlayer, world, pos, state, block);
        if (config.redstone) redstone(out, mc, pos, block, state);
        if (config.blockStates) {
            if (block instanceof BlockCauldron) out.gray("Level: " + state.getValue(BlockCauldron.LEVEL) + "/3");
            if (block == Blocks.lit_furnace) out.text(EnumChatFormatting.GREEN + "Smelting");
            else if (block == Blocks.furnace) out.gray("Idle");
        }
        if (config.spawner && tile instanceof TileEntityMobSpawner) {
            Entity shown = ((TileEntityMobSpawner) tile).getSpawnerBaseLogic().func_180612_a(world);
            if (shown != null) out.gray("Spawns: " + shown.getName());
        }
        if (config.signs && tile instanceof TileEntitySign) {
            for (IChatComponent line : ((TileEntitySign) tile).signText) {
                if (line != null && !line.getUnformattedText().trim().isEmpty()) out.text(line.getFormattedText());
            }
        }
        if (config.banners && tile instanceof TileEntityBanner) banner(out, (TileEntityBanner) tile);
        if (config.modName) {
            ResourceLocation id = Block.blockRegistry.getNameForObject(block);
            out.text(modLine(modName(id == null ? "minecraft" : id.getResourceDomain())));
        }
        return out.finish(icon, config);
    }

    private static ItemStack pick(Block block, MovingObjectPosition hit, World world, BlockPos pos, EntityPlayer player) {
        try {
            ItemStack picked = block.getPickBlock(hit, world, pos, player);
            if (picked != null && picked.getItem() != null) return picked;
        } catch (RuntimeException ignored) {
            // A modded block that needs more context than the vanilla pick: fall back to its item.
        }
        Item item = Item.getItemFromBlock(block);
        return item == null ? null : new ItemStack(item);
    }

    private static ItemStack bucket(Block block) {
        if (block.getMaterial() == Material.water) return new ItemStack(Items.water_bucket);
        if (block.getMaterial() == Material.lava) return new ItemStack(Items.lava_bucket);
        Fluid fluid = FluidRegistry.lookupFluidForBlock(block);
        return fluid == null ? null : FluidContainerRegistry.fillFluidContainer(
            new FluidStack(fluid, FluidContainerRegistry.BUCKET_VOLUME), FluidContainerRegistry.EMPTY_BUCKET);
    }

    private static PropertyInteger intProperty(IBlockState state, String name) {
        for (IProperty property : state.getPropertyNames()) {
            if (property instanceof PropertyInteger && name.equals(property.getName())) return (PropertyInteger) property;
        }
        return null;
    }

    private static void growth(Out out, Block block, IBlockState state) {
        if (!(block instanceof BlockCrops || block instanceof BlockStem || block instanceof BlockCocoa || block instanceof BlockNetherWart)) return;
        PropertyInteger age = intProperty(state, "age");
        if (age == null) return;
        int max = Collections.max(age.getAllowedValues()), value = state.getValue(age);
        out.text(value >= max ? EnumChatFormatting.GREEN + "Growth: Mature" : GRAY + "Growth: " + JadeMath.percent(value, max) + "%");
    }

    /** Forge's harvest tool and level (0 wood or gold, 1 stone, 2 iron, 3 diamond) stand in for 1.20.1's mining tags. */
    private static void harvest(Out out, EntityPlayer player, World world, BlockPos pos, IBlockState state, Block block) {
        float hardness = block.getBlockHardness(world, pos);
        if (hardness < 0) { out.text(EnumChatFormatting.DARK_RED + "Unbreakable"); return; }
        String tool = block.getHarvestTool(state);
        String toolName = tool == null ? null : JadeMath.title(tool);
        if (block.getMaterial().isToolNotRequired()) {
            if (toolName != null && hardness > 0) out.gray("Tool: " + toolName);
            return;
        }
        int level = block.getHarvestLevel(state);
        String tier = level >= 3 ? "Diamond " : level == 2 ? "Iron " : level == 1 ? "Stone " : "";
        boolean ok = player != null && block.canHarvestBlock(world, pos, player);
        out.text(GRAY + "Harvest: " + tier + (toolName == null ? "Correct tool" : toolName) + " "
            + (ok ? EnumChatFormatting.GREEN + "✔" : EnumChatFormatting.RED + "✘"));
    }

    private static void redstone(Out out, Minecraft mc, BlockPos pos, Block block, IBlockState state) {
        PropertyInteger power = intProperty(state, "power");
        if (power != null) out.gray("Power: " + state.getValue(power));
        if (block instanceof BlockRedstoneRepeater) {
            int delay = state.getValue(BlockRedstoneRepeater.DELAY);
            out.gray("Delay: " + delay + " (" + JadeMath.number(delay / 10.0) + "s)");
            if (state.getValue(BlockRedstoneRepeater.LOCKED)) out.gray("Locked");
        }
        if (block instanceof BlockRedstoneComparator) {
            out.gray("Mode: " + (state.getValue(BlockRedstoneComparator.MODE) == BlockRedstoneComparator.Mode.SUBTRACT ? "Subtract" : "Compare"));
        }
        if (block instanceof BlockLever) out.gray("State: " + (state.getValue(BlockLever.POWERED) ? "On" : "Off"));
        if (block instanceof BlockNote) {
            int pitch = note(mc, pos);
            if (pitch >= 0) out.gray("Note: " + JadeMath.noteName(pitch) + " (" + pitch + ")");
            Material below = mc.theWorld.getBlockState(pos.down()).getBlock().getMaterial();
            int instrument = below == Material.rock ? 1 : below == Material.sand ? 2 : below == Material.glass ? 3 : below == Material.wood ? 4 : 0;
            out.gray("Instrument: " + INSTRUMENTS[instrument]);
        }
    }

    /**
     * 1.8.9 never sends a note block's pitch to clients. In singleplayer it is read on the integrated server's own thread and
     * shows from the next refresh; on a server it stays unknown.
     */
    private static int note(Minecraft mc, final BlockPos pos) {
        final IntegratedServer server = mc.getIntegratedServer();
        if (!mc.isSingleplayer() || server == null) return -1;
        final int dimension = mc.theWorld.provider.getDimensionId();
        server.addScheduledTask(() -> {
            TileEntity tile = server.worldServerForDimension(dimension).getTileEntity(pos);
            note = tile instanceof TileEntityNote ? ((TileEntityNote) tile).note : -1;
            notePos = pos;
        });
        return pos.equals(notePos) ? note : -1;
    }

    /** Pattern layers above the base, named as the banner item's tooltip names them. */
    private static void banner(Out out, TileEntityBanner banner) {
        List<TileEntityBanner.EnumBannerPattern> patterns = banner.getPatternList();
        List<EnumDyeColor> colors = banner.getColorList();
        if (patterns == null || colors == null) return;
        for (int i = 1; i < patterns.size() && i < colors.size() && i <= 6; i++) {
            out.gray(StatCollector.translateToLocal("item.banner." + patterns.get(i).getPatternName() + "." + colors.get(i).getUnlocalizedName()));
        }
    }

    // ---- Entities ----

    static Panel entity(Minecraft mc, Entity entity, MovingObjectPosition hit, Jade189.Config config) {
        Out out = new Out(mc.fontRendererObj);
        out.text(entity.getDisplayName().getFormattedText());
        if (entity instanceof EntityLivingBase && !(entity instanceof EntityArmorStand)) {
            EntityLivingBase living = (EntityLivingBase) entity;
            if (config.health) {
                float health = living.getHealth(), max = living.getMaxHealth();
                if (max <= 40) out.hearts(health, max);
                else out.text(EnumChatFormatting.RED + "❤ " + EnumChatFormatting.WHITE + JadeMath.number(health) + " / " + JadeMath.number(max));
            }
            if (config.armor) {
                int armor = living.getTotalArmorValue();
                if (armor > 20) out.gray("Armor: " + armor);
                else if (armor > 0) out.armor(armor);
            }
        }
        if (config.animals) {
            if (entity instanceof EntityAgeable && ((EntityAgeable) entity).isChild()) out.gray("Baby");
            String owner = entity instanceof IEntityOwnable ? ((IEntityOwnable) entity).getOwnerId()
                : entity instanceof EntityHorse && ((EntityHorse) entity).isTame() ? ((EntityHorse) entity).getOwnerId() : null;
            if (owner != null && !owner.isEmpty()) out.gray(owner(mc, owner));
            if (entity instanceof EntityHorse) {
                EntityHorse horse = (EntityHorse) entity;
                out.gray("Speed: " + JadeMath.number(horse.getEntityAttribute(SharedMonsterAttributes.movementSpeed).getAttributeValue() * 42.16) + " blocks/s");
                out.gray("Jump: " + JadeMath.number(JadeMath.jumpHeight(horse.getHorseJumpStrength())) + " blocks");
            }
        }
        if (config.containers && entity instanceof EntityItemFrame && ((EntityItemFrame) entity).getDisplayedItem() != null) {
            out.gray(((EntityItemFrame) entity).getDisplayedItem().getDisplayName());
        }
        if (config.modName) {
            EntityRegistry.EntityRegistration registration = EntityRegistry.instance().lookupModSpawn(entity.getClass(), false);
            out.text(modLine(registration == null ? "Minecraft" : registration.getContainer().getName()));
        }
        return out.finish(entityIcon(entity, hit), config);
    }

    private static String owner(Minecraft mc, String id) {
        try {
            UUID owner = UUID.fromString(id);
            NetworkPlayerInfo info = mc.getNetHandler() == null ? null : mc.getNetHandler().getPlayerInfo(owner);
            if (info != null) return "Owner: " + info.getGameProfile().getName();
            EntityPlayer online = mc.theWorld == null ? null : mc.theWorld.getPlayerEntityByUUID(owner);
            if (online != null) return "Owner: " + online.getName();
        } catch (IllegalArgumentException notUuid) {
            // An owner saved by name before UUIDs: nothing to look up.
        }
        return "Tamed";
    }

    private static ItemStack entityIcon(Entity entity, MovingObjectPosition hit) {
        if (entity instanceof EntityPlayer) {
            ItemStack head = new ItemStack(Items.skull, 1, 3);
            head.setTagInfo("SkullOwner", NBTUtil.writeGameProfile(new NBTTagCompound(), ((EntityPlayer) entity).getGameProfile()));
            return head;
        }
        try {
            ItemStack picked = entity.getPickedResult(hit); // Forge: spawn eggs, and the item of paintings, frames, carts and boats
            return picked != null && picked.getItem() != null ? picked : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    // ---- Shared ----

    private static String modLine(String name) {
        return EnumChatFormatting.BLUE + "" + EnumChatFormatting.ITALIC + name;
    }

    /** Resource domains are lower case; Forge mod ids need not be. */
    static String modName(String domain) {
        return MOD_NAMES.computeIfAbsent(domain, JadeLines189::lookupModName);
    }

    private static String lookupModName(String domain) {
        if ("minecraft".equals(domain)) return "Minecraft";
        for (ModContainer mod : Loader.instance().getModList()) {
            if (mod.getModId().equalsIgnoreCase(domain)) return mod.getName();
        }
        return JadeMath.title(domain);
    }
}
