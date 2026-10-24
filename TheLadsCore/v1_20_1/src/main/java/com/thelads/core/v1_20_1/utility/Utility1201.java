package com.thelads.core.v1_20_1.utility;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.client.BreedingTimer;
import com.thelads.core.client.EnderChestPeek;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.modules.SurvivalGuardsModule;
import com.thelads.core.v1_20_1.hud.items.ShulkerSummary1201;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Parity 1.9.19 utility rows on 1.20.1: Survival Guards (GuardsMixin1201 on MultiPlayerGameMode), Breeding Timer (AnimalMixin1201 tracks
 * the hearts events, drawn here after the world), Ender Chest Peek (filled here from the ender chest screen, shown by
 * EnderTooltipMixin1201), Pack Profiles (PackScreenMixin1201, PackProfilesScreen1201) and Draggable Server List (ServerListDragMixin1201).
 */
public final class Utility1201 {
    /** Animals with a known cooldown, drawn while it runs (few: only those the player saw breed). */
    private static final List<Animal> COOLING = new ArrayList<>();
    /** Survival Guards' action bar lines (one Component each, not one per stopped click). */
    private static final Component TOOL = Component.literal(SurvivalGuardsModule.TOOL_MESSAGE), STRIP = Component.literal(SurvivalGuardsModule.STRIP_MESSAGE),
        AMETHYST = Component.literal(SurvivalGuardsModule.AMETHYST_MESSAGE);
    private static ClientPacketListener connection;
    private static ChestMenu enderMenu;
    /** What the ender chest held when last closed on this server, or null. */
    private static ShulkerSummary1201 ender;

    private Utility1201() {}

    public static void register() {
        ModuleSupport.registerBuiltIn(SurvivalGuardsModule.NAME, BreedingTimer.NAME, EnderChestPeek.NAME,
            com.thelads.core.client.ServerListDrag.NAME);
        ClientTickEvents.END_CLIENT_TICK.register(Utility1201::tick);
        WorldRenderEvents.LAST.register(Utility1201::breedingLabels);
    }

    private static void tick(Minecraft mc) {
        BreedingTimer.tick();
        EnderChestPeek.tick();
        if (mc.getConnection() != connection) { // left or joined a server: forget its ender chest
            connection = mc.getConnection();
            ender = null;
            enderMenu = null;
        }
        LocalPlayer player = mc.player;
        if (!EnderChestPeek.on || player == null) {
            enderMenu = null;
            return;
        }
        if (enderMenu != null && player.containerMenu != enderMenu) { // closed: keep what it held
            Container chest = enderMenu.getContainer();
            List<ItemStack> items = new ArrayList<>(27);
            for (int slot = 0; slot < Math.min(27, chest.getContainerSize()); slot++) items.add(chest.getItem(slot));
            ender = ShulkerSummary1201.of(items);
            enderMenu = null;
        }
        if (enderMenu == null && mc.screen instanceof ContainerScreen screen && isEnderChest(screen.getTitle())) enderMenu = screen.getMenu();
    }

    public static ShulkerSummary1201 ender() { return ender; }

    private static boolean isEnderChest(Component title) {
        return title.getContents() instanceof TranslatableContents key && EnderChestPeek.TITLE_KEY.equals(key.getKey());
    }

    // Survival Guards: MultiPlayerGameMode hooks (GuardsMixin1201) ask before the click is sent.

    /** Breaking a block or attacking with the main-hand item now would wear it past the limit. */
    public static boolean stopsTool(LocalPlayer player) {
        SurvivalGuardsModule guards = SurvivalGuardsModule.get();
        if (guards == null || player.getAbilities().instabuild) return false;
        ItemStack held = player.getMainHandItem();
        return guards.stopsTool(player.isShiftKeyDown(), held.isDamageableItem() ? held.getMaxDamage() : 0, held.getDamageValue())
            && say(TOOL);
    }

    public static boolean stopsBreak(LocalPlayer player, BlockState state) {
        SurvivalGuardsModule guards = SurvivalGuardsModule.get();
        if (guards != null && state.is(Blocks.BUDDING_AMETHYST) && guards.stopsAmethyst(player.isShiftKeyDown()))
            return say(AMETHYST);
        return stopsTool(player);
    }

    /** An axe used on a log, wood or bamboo block that is not stripped yet (what the axe would strip). */
    public static boolean stopsStrip(LocalPlayer player, ItemStack held, BlockState state) {
        SurvivalGuardsModule guards = SurvivalGuardsModule.get();
        if (guards == null || !held.is(ItemTags.AXES) || !(state.is(BlockTags.LOGS) || state.is(BlockTags.BAMBOO_BLOCKS))) return false;
        if (BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().startsWith("stripped_")) return false;
        return guards.stopsStrip(player.isShiftKeyDown()) && say(STRIP);
    }

    private static boolean say(Component message) {
        Minecraft.getInstance().gui.setOverlayMessage(message, false);
        return true;
    }

    // Breeding Timer

    /** Entity event 18 on an animal (AnimalMixin1201): its love, or its breed (then its partner bred too). */
    public static void hearts(Animal animal) {
        long now = animal.level().getGameTime();
        if (!((BreedingTimer.Tracked) animal).lads$breeding().hearts(now)) return;
        if (!COOLING.contains(animal)) COOLING.add(animal);
        Animal partner = null;
        double best = Double.MAX_VALUE;
        for (Animal other : animal.level().getEntitiesOfClass(animal.getClass(), animal.getBoundingBox().inflate(4))) {
            double distance = other.distanceToSqr(animal);
            if (other != animal && distance < best && ((BreedingTimer.Tracked) other).lads$breeding().inLove(now)) {
                best = distance;
                partner = other;
            }
        }
        if (partner != null) {
            ((BreedingTimer.Tracked) partner).lads$breeding().bredAt(now);
            if (!COOLING.contains(partner)) COOLING.add(partner);
        }
    }

    /** After the world: each cooling animal's time left, over it like a name tag (hidden behind blocks). */
    private static void breedingLabels(WorldRenderContext context) {
        if (COOLING.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            COOLING.clear();
            return;
        }
        long now = mc.level.getGameTime();
        for (int i = COOLING.size() - 1; i >= 0; i--) {
            Animal animal = COOLING.get(i);
            if (animal.isRemoved() || animal.level() != mc.level || !((BreedingTimer.Tracked) animal).lads$breeding().cooling(now)) COOLING.remove(i);
        }
        if (!BreedingTimer.on || COOLING.isEmpty()) return;
        Camera camera = context.camera();
        Vec3 eye = camera.getPosition();
        PoseStack pose = context.matrixStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Font font = mc.font;
        float partial = context.tickDelta();
        for (int i = 0; i < COOLING.size(); i++) {
            Animal animal = COOLING.get(i);
            String text = ((BreedingTimer.Tracked) animal).lads$breeding().label(now);
            pose.pushPose();
            pose.translate(Mth.lerp(partial, animal.xOld, animal.getX()) - eye.x,
                Mth.lerp(partial, animal.yOld, animal.getY()) + animal.getBbHeight() + (animal.hasCustomName() ? 0.8 : 0.5) - eye.y,
                Mth.lerp(partial, animal.zOld, animal.getZ()) - eye.z);
            pose.mulPose(camera.rotation());
            pose.scale(-0.025f, -0.025f, 0.025f);
            font.drawInBatch(text, -font.width(text) / 2f, 0, 0xFFFF88CC, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0x40000000,
                LightTexture.FULL_BRIGHT);
            pose.popPose();
        }
        buffers.endBatch();
    }
}
