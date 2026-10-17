package com.thelads.core.v1_8_9.feature;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.SurvivalTweaksModule;
import com.thelads.core.v1_8_9.mixin.EntityRendererAccessor;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraftforge.client.GuiIngameForge;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * QA only (LADS_VERIFY_189_ONLY=parity1910): 1.9.10's StackRefill and SurvivalTweaks ports in the QA world, in survival. Stack
 * Refill (a run-out held stack, a broken tool), Longer Chat History, Keep Bars While Riding (the food bar drawn on a pig), plus
 * the hidden options, cobwebs, obsidian speed, dimension brightness, creeper drops and an animal eating dropped food.
 */
public final class ParityProbe189 {
    private static final Logger LOG = LogManager.getLogger("TheLadsCore");
    private static int tick = -1, passed, failed, foodBars;
    private static BlockPos dirt;
    private static volatile String server = "";
    private static volatile int broken, dropped, still, target;
    private static volatile EntityCow cow;
    private static volatile EntityItem wheat;
    private static float gamma;
    private static final ParityProbe189 LISTENER = new ParityProbe189();

    private ParityProbe189() {}

    @SubscribeEvent
    public void food(RenderGameOverlayEvent.Pre event) {
        if (event.type == RenderGameOverlayEvent.ElementType.FOOD) foodBars++;
    }

    private static void check(boolean ok, String what) {
        if (ok) passed++; else failed++;
        LOG.info("Lads parity probe {}: {}", ok ? "PASS" : "FAIL", what);
    }

    private static void onServer(Minecraft mc, final ServerTask task) {
        final IntegratedServer srv = mc.getIntegratedServer();
        final java.util.UUID id = mc.thePlayer.getUniqueID();
        srv.addScheduledTask(() -> {
            EntityPlayerMP player = srv.getConfigurationManager().getPlayerByUUID(id);
            task.run(player, srv.worldServerForDimension(0));
            player.inventoryContainer.detectAndSendChanges();
        });
    }

    private interface ServerTask { void run(EntityPlayerMP player, WorldServer world); }

    static boolean run(final Minecraft mc) {
        final SurvivalTweaksModule tweaks = SurvivalTweaksModule.get();
        final int held = mc.thePlayer.inventory.currentItem;
        tick++;
        if (tick == 0) {
            check(new net.minecraft.client.network.OldServerPinger() != null, "OldServerPinger loads with the Fast IP Ping redirect");
            check(tweaks.hidden(tweaks.berryBushProtection) && tweaks.hidden(tweaks.elytraWaterSwim) && tweaks.hidden(tweaks.boat360View)
                && !tweaks.hidden(tweaks.noEnchantDurabilityLoss) && !tweaks.hidden(tweaks.keepBarsWhileRiding),
                "the menu hides Berry bush protection, Elytra water swim and Boat 360 View and keeps the others");
            check(Blocks.fire.getFlammability(Blocks.web) == 60 && Blocks.fire.getEncouragement(Blocks.web) == 600, "cobwebs burn (600/60)");
            dirt = new BlockPos(mc.thePlayer).offset(mc.thePlayer.getHorizontalFacing(), 2);
            onServer(mc, (player, world) -> {
                player.setGameType(WorldSettings.GameType.SURVIVAL);
                java.util.Arrays.fill(player.inventory.mainInventory, null);
                player.inventory.mainInventory[held] = new ItemStack(Blocks.cobblestone, 1);
                player.inventory.mainInventory[20] = new ItemStack(Blocks.cobblestone, 32);
                player.inventory.mainInventory[21] = new ItemStack(Items.wooden_pickaxe);
            });
        }
        if (tick == 20) {
            ItemStack stack = mc.thePlayer.getHeldItem();
            check(!mc.thePlayer.capabilities.isCreativeMode && stack != null && stack.stackSize == 1, "survival, holding the last cobblestone");
            mc.thePlayer.dropOneItem(false); // Q: the server takes it out of the hand
        }
        if (tick == 40) {
            ItemStack stack = mc.thePlayer.getHeldItem();
            check(stack != null && stack.getItem() == itemOf(Blocks.cobblestone) && stack.stackSize == 32 && mc.thePlayer.inventory.mainInventory[20] == null,
                "Stack Refill: the run-out held stack came back as the 32 cobblestone from slot 20 (" + stack + ")");
            onServer(mc, (player, world) -> server = String.valueOf(player.inventory.mainInventory[held]) + "/" + player.inventory.mainInventory[20]);
        }
        if (tick == 45) {
            check(server.startsWith("32x") && server.endsWith("/null"), "and the server agrees (" + server + ")");
            onServer(mc, (player, world) -> {
                ItemStack worn = new ItemStack(Items.wooden_pickaxe);
                worn.setItemDamage(worn.getMaxDamage()); // one use left
                player.inventory.mainInventory[held] = worn;
                EntityPig pig = new EntityPig(world);
                pig.setPosition(player.posX + 1.5, player.posY, player.posZ);
                world.spawnEntityInWorld(pig);
                target = pig.getEntityId();
            });
        }
        // A hit with the worn pickaxe breaks it (a tool loses 2 per hit), on this side and the server's, as a left click does.
        // (Mining it in a probe does not work in survival: each tick the unfocused game's own click handling aborts the dig.)
        if (tick == 60) {
            net.minecraft.entity.Entity pig = mc.theWorld.getEntityByID(target);
            check(pig instanceof EntityPig, "a pig to hit with the worn pickaxe");
            if (pig != null) mc.playerController.attackEntity(mc.thePlayer, pig);
        }
        if (tick == 140) {
            ItemStack stack = mc.thePlayer.getHeldItem();
            check(stack != null && stack.getItem() == Items.wooden_pickaxe && stack.getItemDamage() == 0
                && mc.thePlayer.inventory.mainInventory[21] == null, "Stack Refill: the broken pickaxe was replaced by the one from slot 21 (" + stack + ")");
            onServer(mc, (player, world) -> {
                net.minecraft.entity.Entity pig = world.getEntityByID(target);
                if (pig != null) pig.setDead();
                server = player.inventory.mainInventory[held] + "/" + player.inventory.mainInventory[21];
            });
            // Faster obsidian mining: the held pickaxe's speed on obsidian, on and off.
            float on = mc.thePlayer.getBreakSpeed(Blocks.obsidian.getDefaultState(), dirt);
            tweaks.fasterObsidianMining.set(false);
            float off = mc.thePlayer.getBreakSpeed(Blocks.obsidian.getDefaultState(), dirt);
            tweaks.fasterObsidianMining.set(true);
            check(Math.abs(on - off * 1.6F) < 1e-4F && off > 1.0F, "Faster obsidian mining: " + on + " with it, " + off + " without");
            longerChat(mc, tweaks);
            brightness(mc, tweaks, 0);
        }
        if (tick == 145) {
            check(server.equals("1xitem.pickaxeWood@0/null"), "and the server agrees (" + server + ")");
            brightness(mc, tweaks, 1);
        }
        if (tick == 150) brightness(mc, tweaks, 2);
        if (tick == 155) {
            MinecraftForge.EVENT_BUS.register(LISTENER);
            onServer(mc, (player, world) -> {
                EntityPig pig = new EntityPig(world);
                pig.setPosition(player.posX, player.posY, player.posZ);
                world.spawnEntityInWorld(pig);
                player.mountEntity(pig);
            });
        }
        if (tick == 175) foodBars = 0;
        if (tick == 195) {
            check(mc.thePlayer.ridingEntity instanceof EntityPig && GuiIngameForge.renderFood && GuiIngameForge.renderHealthMount && foodBars > 0,
                "Keep Bars While Riding: on a pig the food bar was drawn " + foodBars + " times with the pig's hearts");
            tweaks.keepBarsWhileRiding.set(false);
        }
        if (tick == 200) foodBars = 0;
        if (tick == 220) {
            check(!GuiIngameForge.renderFood && foodBars == 0, "and with it off Forge hides it again (" + foodBars + " draws)");
            tweaks.keepBarsWhileRiding.set(true);
            MinecraftForge.EVENT_BUS.unregister(LISTENER);
            onServer(mc, (player, world) -> {
                net.minecraft.entity.Entity pig = player.ridingEntity;
                player.mountEntity(null);
                if (pig != null) pig.setDead();
                // 100% creeper block drops: a creeper's blast (size 3) on a 5x5 dirt slab 12 blocks off; every dirt or grass block it
                // breaks drops its dirt (vanilla: one in three).
                BlockPos at = new BlockPos(player).offset(EnumFacing.SOUTH, 12);
                for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) world.setBlockState(at.add(x, 0, z), Blocks.dirt.getDefaultState());
                int before = dirtAround(world, at);
                world.createExplosion(new EntityCreeper(world), at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5, 3.0F, true);
                int air = before - dirtAround(world, at);
                int items = 0;
                for (EntityItem item : world.getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(at.add(-6, -6, -6), at.add(7, 7, 7)))) {
                    if (item.getEntityItem().getItem() != itemOf(Blocks.dirt)) continue;
                    items += item.getEntityItem().stackSize;
                    // Stable block drops: spawned at the block's centre with no sideways push.
                    if (item.motionX == 0 && item.motionZ == 0 && item.posX - Math.floor(item.posX) == 0.5 && item.posZ - Math.floor(item.posZ) == 0.5) still++;
                    item.setDead();
                }
                broken = air;
                dropped = items;
                // Animals eat floor food: a cow 4 blocks north with wheat on the ground 3 blocks from it.
                BlockPos north = new BlockPos(player).offset(EnumFacing.NORTH, 6);
                cow = new EntityCow(world);
                cow.setPosition(north.getX() + 0.5, north.getY(), north.getZ() + 0.5);
                world.spawnEntityInWorld(cow);
                wheat = new EntityItem(world, north.getX() + 3.5, north.getY() + 0.2, north.getZ() + 0.5, new ItemStack(Items.wheat, 2));
                world.spawnEntityInWorld(wheat);
            });
        }
        if (tick == 230) {
            check(broken > 0 && dropped == broken, "100% creeper block drops: " + broken + " blocks broken, " + dropped + " dropped");
            check(still == dropped, "Stable block drops: " + still + " of the " + dropped + " drops spawned centred and still");
        }
        if (tick > 230 && tick < 600 && !(cow != null && cow.isInLove())) return false;
        if (tick == 600 || (tick > 230 && tick < 600)) {
            tick = 600;
            check(cow != null && cow.isInLove() && wheat.getEntityItem().stackSize == 1 && !wheat.isDead,
                "Animals eat floor food: the cow walked to the wheat, ate one and fell in love (" + (wheat == null ? null : wheat.getEntityItem()) + " left)");
            onServer(mc, (player, world) -> {
                if (cow != null) cow.setDead();
                if (wheat != null) wheat.setDead();
                java.util.Arrays.fill(player.inventory.mainInventory, null);
                player.setGameType(WorldSettings.GameType.CREATIVE);
            });
        }
        if (tick < 610) return false;
        LOG.info("Lads parity probe END: {} passed, {} failed", passed, failed);
        if (failed > 0) throw new IllegalStateException("1.9.10 parity probe: " + failed + " failed");
        return true;
    }

    private static net.minecraft.item.Item itemOf(net.minecraft.block.Block block) {
        return net.minecraft.item.Item.getItemFromBlock(block);
    }

    private static int dirtAround(WorldServer world, BlockPos at) {
        int n = 0;
        for (BlockPos pos : BlockPos.getAllInBox(at.add(-6, -6, -6), at.add(6, 6, 6))) {
            net.minecraft.block.Block block = world.getBlockState(pos).getBlock();
            if (block == Blocks.dirt || block == Blocks.grass) n++;
        }
        return n;
    }

    /** Longer Chat History (Infinite History off): 150 lines stay, and with it off the vanilla 100 come back. */
    private static void longerChat(Minecraft mc, SurvivalTweaksModule tweaks) {
        com.thelads.core.config.Module chat = ModuleManager.getInstance().getModule("Chat");
        BoolOption infinite = chat != null && chat.getOption("Infinite History") instanceof BoolOption ? (BoolOption) chat.getOption("Infinite History") : null;
        boolean wasInfinite = infinite != null && infinite.get();
        if (infinite != null) infinite.set(false);
        Chat189.History history = (Chat189.History) mc.ingameGUI.getChatGUI();
        for (int i = 0; i < 150; i++) mc.ingameGUI.getChatGUI().printChatMessage(new ChatComponentText("Lads parity chat line " + i));
        int messages = history.ladsMessages(), lines = history.ladsDrawnLines();
        tweaks.longerChatHistory.set(false);
        mc.ingameGUI.getChatGUI().printChatMessage(new ChatComponentText("Lads parity chat line 150"));
        int vanilla = history.ladsMessages();
        tweaks.longerChatHistory.set(true);
        if (infinite != null) infinite.set(wasInfinite);
        check(messages >= 150 && lines >= 150 && vanilla == 100, "Longer Chat History: " + messages + " messages and " + lines
            + " lines kept (vanilla: " + vanilla + ")");
    }

    private static final long[] LIGHT = new long[3];

    /** Dimension brightness 50, 100 and 200 %: the rebuilt lightmap gets brighter with it (Fullbright left off for this). */
    private static void brightness(Minecraft mc, SurvivalTweaksModule tweaks, int phase) {
        int[] colors = ((EntityRendererAccessor) mc.entityRenderer).ladsLightmapColors();
        if (phase > 0) {
            long sum = 0;
            for (int c : colors) sum += (c & 0xFF) + (c >> 8 & 0xFF) + (c >> 16 & 0xFF);
            LIGHT[phase - 1] = sum;
        }
        if (phase < 2) {
            if (phase == 0) gamma = mc.gameSettings.gammaSetting;
            mc.gameSettings.gammaSetting = 0.5F; // 1.8.9's default Brightness is Moody (0), which any % leaves 0
            tweaks.dimensionBrightness.setValue(phase == 0 ? 50 : 200);
        } else {
            tweaks.dimensionBrightness.setValue(100);
            mc.gameSettings.gammaSetting = gamma;
            boolean fullbright = Fullbright189.isEnabled();
            check(fullbright || LIGHT[1] > LIGHT[0], "Dimension brightness: lightmap " + LIGHT[0] + " at 50 %, " + LIGHT[1] + " at 200 %"
                + (fullbright ? " (Fullbright on: not compared)" : ""));
        }
        ((EntityRendererAccessor) mc.entityRenderer).ladsLightmapDirty(true);
    }
}
