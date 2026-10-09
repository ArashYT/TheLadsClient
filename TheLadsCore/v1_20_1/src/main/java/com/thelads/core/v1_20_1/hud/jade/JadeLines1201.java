package com.thelads.core.v1_20_1.hud.jade;

import com.thelads.core.v1_20_1.hud.Hud1201;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.ComparatorMode;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

/** Builds the lines of the Jade panel from what the client knows about the target (no server data). */
final class JadeLines1201 {
    static final int TEXT = 0, HEARTS = 1, ARMOR = 2, ITEMS = 3;
    static final int PAD = 4, LINE = 10, SLOT = 18, ITEMS_PER_ROW = 9, MAX_ITEMS = 18;
    private static final TagKey<Block> NEEDS_NETHERITE = TagKey.create(Registries.BLOCK, new ResourceLocation("forge", "needs_netherite_tool"));
    private static final Map<String, String> MOD_NAMES = new HashMap<>();
    private static boolean providerFailed;

    private JadeLines1201() {}

    /** One drawable row. TEXT uses {@code text}/{@code seq}; HEARTS and ARMOR use {@code value}/{@code max}; ITEMS uses {@code items}. */
    record Line(int kind, Component text, FormattedCharSequence seq, float value, float max, List<ItemStack> items, int width, int height) {
        String plain() {
            return switch (kind) {
                case HEARTS -> "Health: " + JadeMath1201.number(value) + "/" + JadeMath1201.number(max);
                case ARMOR -> "Armor: " + (int) value;
                case ITEMS -> {
                    StringBuilder out = new StringBuilder("Items:");
                    for (ItemStack stack : items) out.append(' ').append(stack.getCount()).append("x ").append(stack.getHoverName().getString()).append(',');
                    yield out.substring(0, out.length() - 1);
                }
                default -> text.getString();
            };
        }
    }

    /** What the panel shows: built at most 5 times a second, drawn every frame. */
    record Panel(ItemStack icon, List<Line> lines, int width, int height) {
        static final Panel EMPTY = new Panel(ItemStack.EMPTY, List.of(), 0, 0);

        List<String> plain() {
            List<String> out = new ArrayList<>(lines.size());
            for (Line line : lines) out.add(line.plain());
            return out;
        }
    }

    private static final class Out {
        final Font font;
        final List<Line> lines = new ArrayList<>();

        Out(Font font) { this.font = font; }

        void text(Component text) {
            FormattedCharSequence seq = text.getVisualOrderText();
            lines.add(new Line(TEXT, text, seq, 0, 0, List.of(), font.width(seq), LINE));
        }

        void gray(String text) { text(Component.literal(text).withStyle(ChatFormatting.GRAY)); }

        void hearts(float health, float max) {
            int containers = Math.max(1, Mth.ceil(max / 2));
            lines.add(new Line(HEARTS, null, null, health, max, List.of(), Math.min(10, containers) * 8 + 1, (containers + 9) / 10 * LINE));
        }

        void armor(int value) {
            lines.add(new Line(ARMOR, null, null, value, 20, List.of(), Mth.ceil(value / 2f) * 8 + 1, LINE));
        }

        void items(List<ItemStack> stacks) {
            List<ItemStack> merged = new ArrayList<>();
            int more = 0;
            outer:
            for (ItemStack stack : stacks) {
                if (stack.isEmpty()) continue;
                for (ItemStack seen : merged) {
                    if (ItemStack.isSameItemSameTags(seen, stack)) { seen.grow(stack.getCount()); continue outer; }
                }
                if (merged.size() < MAX_ITEMS) merged.add(stack.copy()); else more++;
            }
            if (merged.isEmpty()) return;
            int columns = Math.min(ITEMS_PER_ROW, merged.size());
            int rows = (merged.size() + ITEMS_PER_ROW - 1) / ITEMS_PER_ROW;
            lines.add(new Line(ITEMS, null, null, 0, 0, List.copyOf(merged), columns * SLOT - 2, rows * SLOT));
            if (more > 0) gray("+" + more + " more");
        }

        Panel finish(ItemStack icon, JadeConfig1201 config) {
            boolean showIcon = config.icon && !icon.isEmpty();
            int textWidth = 0, textHeight = 0;
            for (Line line : lines) {
                textWidth = Math.max(textWidth, line.width());
                textHeight += line.height();
            }
            int width = PAD * 2 + (showIcon ? 20 : 0) + textWidth;
            int height = PAD * 2 + Math.max(textHeight - 1, showIcon ? 16 : 0);
            return new Panel(showIcon ? icon : ItemStack.EMPTY, List.copyOf(lines), width, height);
        }
    }

    // ---- Blocks ----

    static Panel block(Minecraft mc, BlockPos pos, BlockState state, boolean fluid, JadeConfig1201 config) {
        Level level = mc.level;
        Block block = state.getBlock();
        BlockEntity blockEntity = fluid ? null : level.getBlockEntity(pos);
        Out out = new Out(mc.font);
        out.text(blockEntity instanceof Nameable named && named.hasCustomName() ? named.getCustomName() : block.getName());

        if (config.growth) growth(out, block, state);
        if (config.harvest && !fluid) harvest(out, mc.player, level, pos, state);
        if (config.redstone) redstone(out, block, state);
        if (config.blockStates) blockStates(out, block, state);
        if (config.spawner && blockEntity instanceof SpawnerBlockEntity spawner) {
            Entity shown = spawner.getSpawner().getOrCreateDisplayEntity(level, level.getRandom(), pos);
            if (shown != null) out.text(Component.literal("Spawns: ").withStyle(ChatFormatting.GRAY).append(shown.getType().getDescription()));
        }
        if (config.signs && blockEntity instanceof SignBlockEntity sign) {
            boolean filtered = mc.isTextFilteringEnabled();
            for (int i = 0; i < 4; i++) signLine(out, sign.getFrontText().getMessage(i, filtered), false);
            for (int i = 0; i < 4; i++) signLine(out, sign.getBackText().getMessage(i, filtered), true);
        }
        if (config.banners && blockEntity instanceof BannerBlockEntity banner) {
            var patterns = banner.getPatterns();
            for (int i = 1; i < patterns.size() && i <= 6; i++) {
                var layer = patterns.get(i);
                String color = layer.getSecond().getName();
                layer.getFirst().unwrapKey().ifPresent(key -> out.text(Component.translatable(
                    "block.minecraft.banner." + key.location().toShortLanguageKey() + "." + color).withStyle(ChatFormatting.GRAY)));
            }
        }
        if (config.containers && blockEntity != null) contents(out, blockEntity);
        if (!JadeProvider1201.PROVIDERS.isEmpty()) {
            List<Component> extra = new ArrayList<>();
            for (JadeProvider1201 provider : JadeProvider1201.PROVIDERS) {
                try { provider.appendBlock(state, pos, blockEntity, extra); } catch (RuntimeException e) { providerError(e); }
            }
            extra.forEach(out::text);
        }
        if (config.modName) out.text(modLine(BuiltInRegistries.BLOCK.getKey(block).getNamespace()));
        return out.finish(blockIcon(level, pos, state, fluid), config);
    }

    private static ItemStack blockIcon(Level level, BlockPos pos, BlockState state, boolean fluid) {
        if (fluid) return new ItemStack(state.getFluidState().getType().getBucket());
        try {
            ItemStack picked = state.getBlock().getCloneItemStack(level, pos, state);
            if (!picked.isEmpty()) return picked;
        } catch (RuntimeException ignored) {
            // A modded block that needs more context than the vanilla pick: fall back to its item.
        }
        return new ItemStack(state.getBlock());
    }

    private static void growth(Out out, Block block, BlockState state) {
        if (!(block instanceof CropBlock || block instanceof StemBlock || block instanceof CocoaBlock || block instanceof NetherWartBlock
            || block instanceof SweetBerryBushBlock || block instanceof PitcherCropBlock)) return;
        for (Property<?> property : state.getProperties()) {
            if (property instanceof IntegerProperty age && "age".equals(age.getName())) {
                int max = Collections.max(age.getPossibleValues());
                int value = state.getValue(age);
                out.text(value >= max ? Component.literal("Growth: Mature").withStyle(ChatFormatting.GREEN)
                    : Component.literal("Growth: " + JadeMath1201.percent(value, max) + "%").withStyle(ChatFormatting.GRAY));
                return;
            }
        }
    }

    private static void harvest(Out out, Player player, Level level, BlockPos pos, BlockState state) {
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0) { out.text(Component.literal("Unbreakable").withStyle(ChatFormatting.DARK_RED)); return; }
        String tool = state.is(BlockTags.MINEABLE_WITH_PICKAXE) ? "Pickaxe" : state.is(BlockTags.MINEABLE_WITH_AXE) ? "Axe"
            : state.is(BlockTags.MINEABLE_WITH_SHOVEL) ? "Shovel" : state.is(BlockTags.MINEABLE_WITH_HOE) ? "Hoe" : null;
        if (!state.requiresCorrectToolForDrops()) {
            if (tool != null && hardness > 0) out.gray("Tool: " + tool);
            return;
        }
        String tier = state.is(NEEDS_NETHERITE) ? "Netherite " : state.is(BlockTags.NEEDS_DIAMOND_TOOL) ? "Diamond "
            : state.is(BlockTags.NEEDS_IRON_TOOL) ? "Iron " : state.is(BlockTags.NEEDS_STONE_TOOL) ? "Stone " : "";
        boolean ok = player != null && player.hasCorrectToolForDrops(state);
        out.text(Component.literal("Harvest: " + tier + (tool == null ? "Correct tool" : tool) + " ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(ok ? "✔" : "✘").withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED)));
    }

    private static void redstone(Out out, Block block, BlockState state) {
        if (state.hasProperty(BlockStateProperties.POWER)) out.gray("Power: " + state.getValue(BlockStateProperties.POWER));
        if (block instanceof RepeaterBlock) {
            int delay = state.getValue(RepeaterBlock.DELAY);
            out.gray("Delay: " + delay + " (" + JadeMath1201.number(delay / 10.0) + "s)");
            if (state.getValue(RepeaterBlock.LOCKED)) out.gray("Locked");
        }
        if (block instanceof ComparatorBlock) {
            out.gray("Mode: " + (state.getValue(ComparatorBlock.MODE) == ComparatorMode.SUBTRACT ? "Subtract" : "Compare"));
        }
        if (block instanceof LeverBlock) out.gray("State: " + (state.getValue(LeverBlock.POWERED) ? "On" : "Off"));
        if (block instanceof NoteBlock) {
            int note = state.getValue(NoteBlock.NOTE);
            out.gray("Note: " + JadeMath1201.noteName(note) + " (" + note + ")");
            out.gray("Instrument: " + JadeMath1201.title(state.getValue(NoteBlock.INSTRUMENT).getSerializedName()));
        }
    }

    private static void blockStates(Out out, Block block, BlockState state) {
        if (state.hasProperty(BlockStateProperties.LEVEL_HONEY)) out.gray("Honey: " + state.getValue(BlockStateProperties.LEVEL_HONEY) + "/5");
        if (block instanceof ComposterBlock) {
            int level = state.getValue(ComposterBlock.LEVEL);
            out.gray(level >= 8 ? "Compost: Ready" : "Compost: " + level + "/7");
        }
        if (state.hasProperty(BlockStateProperties.LEVEL_CAULDRON)) out.gray("Level: " + state.getValue(BlockStateProperties.LEVEL_CAULDRON) + "/3");
        if (state.hasProperty(BlockStateProperties.RESPAWN_ANCHOR_CHARGES)) {
            out.gray("Charges: " + state.getValue(BlockStateProperties.RESPAWN_ANCHOR_CHARGES) + "/4");
        }
        if (block instanceof ChiseledBookShelfBlock) {
            int books = 0;
            for (BooleanProperty slot : ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES) if (state.getValue(slot)) books++;
            out.gray("Books: " + books + "/6");
        }
        if (state.hasProperty(BlockStateProperties.LIT)) {
            boolean lit = state.getValue(BlockStateProperties.LIT);
            if (block instanceof AbstractFurnaceBlock) out.text(lit ? Component.literal("Smelting").withStyle(ChatFormatting.GREEN) : Component.literal("Idle").withStyle(ChatFormatting.GRAY));
            else if (block instanceof CampfireBlock) out.gray(lit ? "Lit" : "Unlit");
        }
    }

    private static void signLine(Out out, Component line, boolean back) {
        if (line.getString().isBlank()) return;
        out.text(back ? Component.empty().append(line).withStyle(ChatFormatting.DARK_GRAY) : line);
    }

    /** Items the client really has for this block entity: campfire food, brushable loot, and any container the server syncs. */
    private static void contents(Out out, BlockEntity blockEntity) {
        if (blockEntity instanceof CampfireBlockEntity campfire) { out.items(campfire.getItems()); return; }
        if (blockEntity instanceof BrushableBlockEntity brushable) { out.items(List.of(brushable.getItem())); return; }
        if (blockEntity instanceof Container container) {
            int size = Math.min(container.getContainerSize(), 256);
            List<ItemStack> stacks = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                ItemStack stack = container.getItem(i);
                if (!stack.isEmpty()) stacks.add(stack);
            }
            out.items(stacks);
        }
    }

    // ---- Entities ----

    static Panel entity(Minecraft mc, Entity entity, JadeConfig1201 config) {
        Out out = new Out(mc.font);
        out.text(entity.getDisplayName());
        if (entity instanceof LivingEntity living && !(entity instanceof ArmorStand)) {
            if (config.health) {
                float health = living.getHealth(), max = living.getMaxHealth();
                if (max <= 40) out.hearts(health, max);
                else out.text(Component.literal("❤ ").withStyle(ChatFormatting.RED)
                    .append(Component.literal(JadeMath1201.number(health) + " / " + JadeMath1201.number(max)).withStyle(ChatFormatting.WHITE)));
            }
            if (config.armor) {
                int armor = living.getArmorValue();
                if (armor > 20) out.gray("Armor: " + armor);
                else if (armor > 0) out.armor(armor);
            }
        }
        if (config.animals) {
            if (entity instanceof AgeableMob ageable && ageable.isBaby()) out.gray("Baby");
            if (entity instanceof OwnableEntity ownable && ownable.getOwnerUUID() != null) out.gray(owner(mc, ownable.getOwnerUUID()));
            if (entity instanceof AbstractHorse horse) {
                out.gray("Speed: " + JadeMath1201.number(horse.getAttributeValue(Attributes.MOVEMENT_SPEED) * 42.16) + " blocks/s");
                out.gray("Jump: " + JadeMath1201.number(JadeMath1201.jumpHeight(horse.getAttributeValue(Attributes.JUMP_STRENGTH))) + " blocks");
            }
            if (entity instanceof Villager villager) {
                out.text(Component.translatable("merchant.level." + villager.getVillagerData().getLevel()).withStyle(ChatFormatting.GRAY));
            }
        }
        if (config.containers && entity instanceof ItemFrame frame && !frame.getItem().isEmpty()) {
            out.text(Component.empty().append(frame.getItem().getHoverName()).withStyle(ChatFormatting.GRAY));
        }
        if (!JadeProvider1201.PROVIDERS.isEmpty()) {
            List<Component> extra = new ArrayList<>();
            for (JadeProvider1201 provider : JadeProvider1201.PROVIDERS) {
                try { provider.appendEntity(entity, extra); } catch (RuntimeException e) { providerError(e); }
            }
            extra.forEach(out::text);
        }
        if (config.modName) out.text(modLine(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getNamespace()));
        return out.finish(entityIcon(entity), config);
    }

    private static String owner(Minecraft mc, UUID owner) {
        PlayerInfo info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(owner);
        if (info != null) return "Owner: " + info.getProfile().getName();
        Player online = mc.level == null ? null : mc.level.getPlayerByUUID(owner);
        return online != null ? "Owner: " + online.getGameProfile().getName() : "Tamed";
    }

    private static ItemStack entityIcon(Entity entity) {
        if (entity instanceof Player player) {
            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            head.getOrCreateTag().put("SkullOwner", NbtUtils.writeGameProfile(new CompoundTag(), player.getGameProfile()));
            return head;
        }
        try {
            ItemStack picked = entity.getPickResult();
            if (picked != null && !picked.isEmpty()) return picked;
        } catch (RuntimeException ignored) {
            // Modded entity whose pick needs a hit result: try Forge's egg lookup below.
        }
        return forgeSpawnEgg(entity.getType());
    }

    /** Forge mod mobs keep their spawn eggs outside the vanilla egg map (ForgeSpawnEggItem.fromEntityType). */
    private static ItemStack forgeSpawnEgg(EntityType<?> type) {
        try {
            Object egg = Class.forName("net.minecraftforge.common.ForgeSpawnEggItem").getMethod("fromEntityType", EntityType.class).invoke(null, type);
            if (egg instanceof net.minecraft.world.item.Item item) return new ItemStack(item);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Plain Fabric.
        }
        return ItemStack.EMPTY;
    }

    // ---- Shared ----

    private static Component modLine(String namespace) {
        return Component.literal(modName(namespace)).withStyle(ChatFormatting.BLUE, ChatFormatting.ITALIC);
    }

    static String modName(String namespace) {
        return MOD_NAMES.computeIfAbsent(namespace, JadeLines1201::lookupModName);
    }

    private static String lookupModName(String namespace) {
        if ("minecraft".equals(namespace)) return "Minecraft";
        Optional<net.fabricmc.loader.api.ModContainer> fabric = FabricLoader.getInstance().getModContainer(namespace);
        if (fabric.isPresent()) return fabric.get().getMetadata().getName();
        try {
            // Forge mods under Sinytra Connector: ModList.get().getModContainerById(id).getModInfo().getDisplayName().
            Object list = Class.forName("net.minecraftforge.fml.ModList").getMethod("get").invoke(null);
            Object found = list.getClass().getMethod("getModContainerById", String.class).invoke(list, namespace);
            if (found instanceof Optional<?> container && container.isPresent()) {
                Object info = Class.forName("net.minecraftforge.fml.ModContainer").getMethod("getModInfo").invoke(container.get());
                Object name = Class.forName("net.minecraftforge.forgespi.language.IModInfo").getMethod("getDisplayName").invoke(info);
                if (name instanceof String text && !text.isBlank()) return text;
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Plain Fabric: no Forge mod list.
        }
        return JadeMath1201.title(namespace);
    }

    private static void providerError(RuntimeException e) {
        if (providerFailed) return;
        providerFailed = true;
        Hud1201.LOG.warn("Jade: an addon provider failed (further errors are not logged)", e);
    }
}
