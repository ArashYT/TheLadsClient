package com.thelads.core.v1_8_9.feature;

import static com.thelads.core.v1_8_9.feature.CoreProbe.after;
import static com.thelads.core.v1_8_9.feature.CoreProbe.check;
import static com.thelads.core.v1_8_9.feature.CoreProbe.retry;
import static com.thelads.core.v1_8_9.feature.CoreProbe.screenshot;

import com.google.gson.JsonElement;
import com.thelads.core.config.Option;
import com.thelads.core.modules.ItemPhysicsModule;
import com.thelads.core.v1_8_9.gui.LadsSettingsScreen189;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.apache.logging.log4j.LogManager;

/**
 * QA only: Item Physics (ItemPhysics189), run by CoreProbe in its QA world (LADS_VERIFY_189_ONLY=itemphysics). A stone arena with
 * a water pool, a lava pool and a cactus at y 200; real items on the integrated server; screenshots lads-qa/screenshots/170-ip-*.png.
 * Module off is compared with the client's timer stopped (everything drawn stands still): off, on, off again must give the same
 * pixels. Arena, items, player, inventory and settings are put back.
 */
final class Probe170ItemPhysics {
    private static final Map<Option, JsonElement> optionsWere = new LinkedHashMap<Option, JsonElement>();
    private static final Map<BlockPos, IBlockState> blocksWere = new LinkedHashMap<BlockPos, IBlockState>();
    private static final Map<String, Object> facts = new ConcurrentHashMap<String, Object>();
    private static final List<Integer> spawned = new java.util.concurrent.CopyOnWriteArrayList<Integer>();
    private static boolean wasEnabled, started, guiWas;
    private static long modifiedWas;
    private static BlockPos base;
    private static double[] posWas;
    private static int sample;
    private static FrozenFrames frozen;
    private static ItemStack[] inventoryWas;

    private interface ServerTask { void run(EntityPlayerMP player); }

    static final List<CoreProbe.Step> STEPS = Arrays.<CoreProbe.Step>asList(Probe170ItemPhysics::start,
        mc -> {
            if (!ready("built")) return retry(5); // 1.8.9 relights every block of the arena: it can take seconds
            check(true, "Item Physics: the arena (stone floor, water and lava pools, cactus) is built at y 200");
            view(mc, 0.5, 1, -2.5, 0, 30);
            onServer(mc, player -> {
                Item[] row = {Items.diamond_sword, Items.apple, Items.stick, Item.getItemFromBlock(Blocks.stone), Item.getItemFromBlock(Blocks.log),
                    Item.getItemFromBlock(Blocks.torch), Item.getItemFromBlock(Blocks.chest), Item.getItemFromBlock(Blocks.cobblestone), Items.paper};
                int[] counts = {1, 1, 1, 1, 1, 1, 1, 64, 32};
                for (int i = 0; i < row.length; i++) spawn(player.worldObj, row[i], counts[i], i - 3.5, 1.3, 2.5).setNoDespawn();
            });
            return after(60);
        },
        mc -> { screenshot(mc, "170-ip-rest-side"); view(mc, 0.5, 5, 2.5, 0, 90); return after(20); },
        mc -> { screenshot(mc, "170-ip-rest-top"); view(mc, 0.5, 1, -2.5, 0, 30); return after(20); },
        mc -> { // module off, on, off with the client's timer stopped
            frozen = new FrozenFrames(mc);
            MinecraftForge.EVENT_BUS.register(frozen);
            return after(1);
        },
        mc -> {
            if (!frozen.done) return retry(1);
            MinecraftForge.EVENT_BUS.unregister(frozen);
            module().setEnabled(true);
            check(frozen.offAgain == 0, "Item Physics: module off draws the frozen scene pixel for pixel as before it was on");
            check(frozen.onChanged > 1000, "Item Physics: module on changes that scene (" + frozen.onChanged + " pixels differ)");
            view(mc, 0.5, 1.6, -3, 0, 5);
            onServer(mc, player -> {
                toss(spawn(player.worldObj, Items.iron_sword, 1, -0.5, 1.2, 0.5), -0.08, 0.42, 0.02);
                toss(spawn(player.worldObj, Items.golden_apple, 1, 0.5, 1.2, 0.5), 0.02, 0.45, 0.03);
                toss(spawn(player.worldObj, Item.getItemFromBlock(Blocks.brick_block), 1, 1.5, 1.2, 0.5), 0.09, 0.4, 0.01);
                toss(spawn(player.worldObj, Items.feather, 1, 2.5, 1.2, 0.5), 0.05, 0.38, -0.02);
            });
            return after(7);
        },
        mc -> { screenshot(mc, "170-ip-tumble-midair"); return after(40); },
        mc -> { screenshot(mc, "170-ip-tumble-landed"); return after(1); },
        mc -> { // water: wood, sticks and wool float; stone, iron and diamonds sink
            view(mc, 3.5, 3.2, -0.6, 180, 50);
            onServer(mc, player -> {
                Item[] items = {Item.getItemFromBlock(Blocks.planks), Items.stick, Item.getItemFromBlock(Blocks.wool), Item.getItemFromBlock(Blocks.stone),
                    Items.iron_ingot, Items.diamond};
                for (int i = 0; i < items.length; i++) facts.put("water-id-" + i, spawn(player.worldObj, items[i], 1, 2.5 + i % 3, 0.8, -4.5 + i / 3).getEntityId());
            });
            return after(80);
        },
        mc -> {
            onServer(mc, player -> { for (int i = 0; i < 6; i++) record(player.worldObj, "water-" + i, (Integer) facts.get("water-id-" + i)); });
            screenshot(mc, "170-ip-water");
            return after(4);
        },
        mc -> {
            if (!ready("water-5")) return retry(2);
            String[] names = {"oak planks", "a stick", "white wool", "stone", "an iron ingot", "a diamond"};
            for (int i = 0; i < 6; i++) {
                double y = fact("water-" + i)[1];
                if (i < 3) check(y > 0.4, "Item Physics: " + names[i] + " floats at the surface (y " + fmt(y) + ")");
                else check(y < -0.6, "Item Physics: " + names[i] + " sinks to the bottom (y " + fmt(y) + ")");
            }
            view(mc, 3.5, 4.5, -4, 0, 90);
            return after(15);
        },
        mc -> {
            screenshot(mc, "170-ip-water-top");
            module().setEnabled(false); // control: vanilla 1.8.9 sinks every item
            onServer(mc, player -> facts.put("water-off-id", spawn(player.worldObj, Item.getItemFromBlock(Blocks.planks), 1, 3.5, 0.8, -4).getEntityId()));
            return after(80);
        },
        mc -> { onServer(mc, player -> record(player.worldObj, "water-off", (Integer) facts.get("water-off-id"))); return after(4); },
        mc -> {
            if (!ready("water-off")) return retry(2);
            check(fact("water-off")[1] < -0.6, "Item Physics: control, module off: oak planks sink as in vanilla 1.8.9 (y " + fmt(fact("water-off")[1]) + ")");
            module().setEnabled(true);
            view(mc, -3, 3.2, -0.6, 180, 50);
            onServer(mc, player -> {
                Item[] items = {Item.getItemFromBlock(Blocks.planks), Item.getItemFromBlock(Blocks.cobblestone), Items.iron_ingot, Items.gold_ingot};
                for (int i = 0; i < items.length; i++) facts.put("lava-id-" + i, spawn(player.worldObj, items[i], 1, -3.6 + (i % 2) * 1.2, 0.9, -4.6 + (i / 2) * 1.2).getEntityId());
            });
            return after(80);
        },
        mc -> {
            onServer(mc, player -> { for (int i = 0; i < 4; i++) record(player.worldObj, "lava-" + i, (Integer) facts.get("lava-id-" + i)); });
            screenshot(mc, "170-ip-lava");
            return after(4);
        },
        mc -> {
            if (!ready("lava-3")) return retry(2);
            check(fact("lava-0")[3] == 0, "Item Physics: oak planks burn up in lava");
            String[] names = {"", "cobblestone", "an iron ingot", "a gold ingot"};
            for (int i = 1; i < 4; i++)
                check(fact("lava-" + i)[3] == 1 && fact("lava-" + i)[1] > 0.3, "Item Physics: " + names[i] + " survives the lava, floating (y " + fmt(fact("lava-" + i)[1]) + ")");
            module().setEnabled(false);
            onServer(mc, player -> facts.put("lava-off-id", spawn(player.worldObj, Item.getItemFromBlock(Blocks.cobblestone), 1, -3, 0.9, -4).getEntityId()));
            return after(80);
        },
        mc -> { onServer(mc, player -> record(player.worldObj, "lava-off", (Integer) facts.get("lava-off-id"))); return after(4); },
        mc -> {
            if (!ready("lava-off")) return retry(2);
            check(fact("lava-off")[3] == 0, "Item Physics: control, module off: cobblestone burns in lava as vanilla");
            module().setEnabled(true);
            view(mc, 0.5, 3.6, 1.2, 0, 35);
            onServer(mc, player -> facts.put("cactus-on-id", spawn(player.worldObj, Item.getItemFromBlock(Blocks.dirt), 1, 0.5, 3.3, 4.5).getEntityId()));
            return after(50);
        },
        mc -> {
            onServer(mc, player -> record(player.worldObj, "cactus-on", (Integer) facts.get("cactus-on-id")));
            screenshot(mc, "170-ip-cactus");
            return after(4);
        },
        mc -> {
            if (!ready("cactus-on")) return retry(2);
            check(fact("cactus-on")[3] == 1 && fact("cactus-on")[1] > 2.5, "Item Physics: an item lying on a cactus survives (y " + fmt(fact("cactus-on")[1]) + ")");
            module().setEnabled(false);
            onServer(mc, player -> {
                Entity old = player.worldObj.getEntityByID((Integer) facts.get("cactus-on-id"));
                if (old != null) old.setDead();
                facts.put("cactus-off-id", spawn(player.worldObj, Item.getItemFromBlock(Blocks.dirt), 1, 0.5, 3.3, 4.5).getEntityId());
            });
            return after(50);
        },
        mc -> { onServer(mc, player -> record(player.worldObj, "cactus-off", (Integer) facts.get("cactus-off-id"))); return after(4); },
        mc -> {
            if (!ready("cactus-off")) return retry(2);
            check(fact("cactus-off")[3] == 0, "Item Physics: control, module off: the cactus destroys the item as vanilla");
            module().setEnabled(true);
            view(mc, -2.5, 2.5, 4, 90, 40);
            onServer(mc, player -> {
                EntityItem stick = spawn(player.worldObj, Items.stick, 1, -4.5, 1.05, 5.5);
                stick.setFire(4);
                EntityItem stone = spawn(player.worldObj, Item.getItemFromBlock(Blocks.cobblestone), 1, -4.5, 1.05, 3.5);
                stone.setFire(4);
                facts.put("stone-burning", stone.isBurning());
            });
            return after(30);
        },
        mc -> {
            onServer(mc, player -> {
                facts.put("fire-stick", player.worldObj.getBlockState(base.add(-5, 1, 5)).getBlock() == Blocks.fire);
                facts.put("fire-stone", player.worldObj.getBlockState(base.add(-5, 1, 3)).getBlock() == Blocks.fire);
            });
            screenshot(mc, "170-ip-ignite");
            return after(4);
        },
        mc -> {
            if (!ready("fire-stick", "fire-stone", "stone-burning")) return retry(2);
            check(Boolean.TRUE.equals(facts.get("fire-stick")), "Item Physics: a burning stick lying on oak planks sets them alight");
            check(Boolean.FALSE.equals(facts.get("stone-burning")) && Boolean.FALSE.equals(facts.get("fire-stone")),
                "Item Physics: cobblestone cannot be set on fire, so it lights nothing");
            onServer(mc, player -> {
                for (int x = -6; x <= -4; x++) for (int z = 2; z <= 6; z++) for (int y = 0; y <= 2; y++)
                    if (player.worldObj.getBlockState(base.add(x, y, z)).getBlock() == Blocks.fire) set(player.worldObj, base.add(x, y, z), Blocks.air.getDefaultState());
                clearItems(player.worldObj);
            });
            // right-click pickup: no auto pickup for the host; the use key on the item picks it up
            module().pickup.set(true);
            view(mc, -5.5, 1, 0.5, -90, 0);
            onServer(mc, player -> {
                player.capabilities.isFlying = false;
                player.sendPlayerAbilities();
                EntityItem emerald = new EntityItem(player.worldObj, player.posX + 1.2, player.posY + 0.2, player.posZ, new ItemStack(Items.emerald));
                emerald.motionX = emerald.motionY = emerald.motionZ = 0;
                player.worldObj.spawnEntityInWorld(emerald);
                spawned.add(emerald.getEntityId());
                facts.put("emerald", emerald.getEntityId());
            });
            mc.thePlayer.capabilities.isFlying = false;
            return after(40);
        },
        mc -> {
            onServer(mc, player -> facts.put("emerald-waiting", player.worldObj.getEntityByID((Integer) facts.get("emerald")) != null
                && !player.worldObj.getEntityByID((Integer) facts.get("emerald")).isDead && !player.inventory.hasItem(Items.emerald)));
            Entity item = mc.theWorld.getEntityByID((Integer) facts.get("emerald"));
            if (item != null) {
                double dx = item.posX - mc.thePlayer.posX, dy = item.posY + 0.1 - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight()), dz = item.posZ - mc.thePlayer.posZ;
                mc.thePlayer.rotationYaw = mc.thePlayer.prevRotationYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                mc.thePlayer.rotationPitch = mc.thePlayer.prevRotationPitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
            }
            return after(3);
        },
        mc -> {
            if (!ready("emerald-waiting")) return retry(2);
            check(Boolean.TRUE.equals(facts.get("emerald-waiting")), "Item Physics: right-click pickup on: an emerald at the player's feet is not picked up by walking into it");
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
            return after(2);
        },
        mc -> { KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false); return after(10); },
        mc -> {
            onServer(mc, player -> facts.put("emerald-picked", (player.worldObj.getEntityByID((Integer) facts.get("emerald")) == null
                || player.worldObj.getEntityByID((Integer) facts.get("emerald")).isDead) && player.inventory.hasItem(Items.emerald)));
            return after(4);
        },
        mc -> {
            if (!ready("emerald-picked")) return retry(2);
            check(Boolean.TRUE.equals(facts.get("emerald-picked")), "Item Physics: the use key on the emerald picks it up into the inventory");
            module().pickup.set(false);
            mc.thePlayer.rotationPitch = mc.thePlayer.prevRotationPitch = 0;
            mc.thePlayer.rotationYaw = mc.thePlayer.prevRotationYaw = -90;
            onServer(mc, player -> {
                EntityItem gold = new EntityItem(player.worldObj, player.posX + 0.3, player.posY + 0.2, player.posZ, new ItemStack(Items.gold_ingot));
                gold.motionX = gold.motionY = gold.motionZ = 0;
                player.worldObj.spawnEntityInWorld(gold);
                spawned.add(gold.getEntityId());
            });
            return after(30);
        },
        mc -> { onServer(mc, player -> facts.put("gold-picked", player.inventory.hasItem(Items.gold_ingot))); return after(4); },
        mc -> {
            if (!ready("gold-picked")) return retry(2);
            check(Boolean.TRUE.equals(facts.get("gold-picked")), "Item Physics: right-click pickup off: walking into a gold ingot picks it up as vanilla");
            onServer(mc, player -> {
                player.inventory.clear();
                player.inventory.setInventorySlotContents(0, new ItemStack(Blocks.cobblestone, 64));
            });
            mc.thePlayer.inventory.currentItem = 0;
            return after(10);
        },
        // charged throw: a tap throws as vanilla; holding the drop key throws further
        mc -> { KeyBinding.setKeyBindState(mc.gameSettings.keyBindDrop.getKeyCode(), true); return after(1); },
        mc -> { KeyBinding.setKeyBindState(mc.gameSettings.keyBindDrop.getKeyCode(), false); return after(50); },
        mc -> { thrown(mc, "throw-tap"); return after(4); },
        mc -> { KeyBinding.setKeyBindState(mc.gameSettings.keyBindDrop.getKeyCode(), true); sample = 0; return after(0); },
        mc -> {
            if (++sample == 16) screenshot(mc, "170-ip-throw-charging");
            if (sample < 26) return retry(0);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindDrop.getKeyCode(), false);
            return after(6);
        },
        mc -> { screenshot(mc, "170-ip-throw-midair"); return after(60); },
        mc -> { thrown(mc, "throw-charged"); return after(4); },
        mc -> {
            if (!ready("throw-tap", "throw-charged")) return retry(2);
            double tap = fact("throw-tap")[0], charged = fact("throw-charged")[0];
            check(tap > 0.5 && charged > tap * 1.5, "Item Physics: a charged throw lands further than a tap (" + fmt(charged) + " vs " + fmt(tap) + " blocks)");
            LadsSettingsScreen189 menu = new LadsSettingsScreen189(null);
            mc.displayGuiScreen(menu);
            menu.ui().openModule(ItemPhysicsModule.NAME);
            return after(20);
        },
        mc -> { screenshot(mc, "170-ip-settings"); mc.displayGuiScreen(null); return after(5); },
        mc -> { // despawn time: Forge's lifespan follows the slider; an item at the edge of its lifespan goes on time
            module().despawn.setValue(1);
            onServer(mc, player -> facts.put("despawn-1", spawn(player.worldObj, Items.coal, 1, 8.5, 1.1, 0.5).getEntityId()));
            return after(2);
        },
        mc -> {
            module().despawn.setValue(10);
            onServer(mc, player -> {
                facts.put("despawn-10", spawn(player.worldObj, Items.dye, 1, 9.5, 1.1, 0.5).getEntityId());
                EntityItem one = (EntityItem) player.worldObj.getEntityByID((Integer) facts.get("despawn-1"));
                EntityItem ten = (EntityItem) player.worldObj.getEntityByID((Integer) facts.get("despawn-10"));
                facts.put("lifespans", new double[]{one.lifespan, ten.lifespan});
                ReflectionHelper.setPrivateValue(EntityItem.class, one, 1150, "age", "field_70292_b");
                ReflectionHelper.setPrivateValue(EntityItem.class, ten, 6100, "age", "field_70292_b");
            });
            return after(3);
        },
        mc -> {
            onServer(mc, player -> {
                facts.put("one-early", player.worldObj.getEntityByID((Integer) facts.get("despawn-1")) != null);
                facts.put("ten-late", player.worldObj.getEntityByID((Integer) facts.get("despawn-10")) != null);
            });
            return after(70);
        },
        mc -> {
            onServer(mc, player -> facts.put("one-gone", player.worldObj.getEntityByID((Integer) facts.get("despawn-1")) == null));
            return after(4);
        },
        mc -> {
            if (!ready("lifespans", "one-early", "ten-late", "one-gone")) return retry(2);
            double[] lifespans = (double[]) facts.get("lifespans");
            check(lifespans[0] == 1200 && lifespans[1] == 12000, "Item Physics: despawn 1 and 10 minutes give lifespans " + (int) lifespans[0] + " and " + (int) lifespans[1]);
            check(Boolean.TRUE.equals(facts.get("one-early")) && Boolean.TRUE.equals(facts.get("one-gone")), "Item Physics: 1-minute despawn: there at about 1150 ticks, gone 70 ticks later");
            check(Boolean.TRUE.equals(facts.get("ten-late")), "Item Physics: 10-minute despawn: still there at 6100 ticks, past vanilla's 6000");
            return after(1);
        },
        mc -> { stop(); return after(10); });

    private static ItemPhysicsModule module() { return ItemPhysics189.module(); }

    private static boolean start(Minecraft mc) {
        ItemPhysicsModule module = module();
        wasEnabled = module.isEnabled();
        modifiedWas = module.getLastModified();
        for (Option option : module.getOptions()) optionsWere.put(option, option.save());
        for (Option option : module.getOptions()) option.reset();
        module.setEnabled(true);
        LogManager.getLogger("TheLadsCore").info("Lads 1.8.9 item physics rules: this client's world {}, integrated server {}; a remote server's world "
            + "is only a client world with no integrated server, so rules() is null there and every rule stays off",
            ItemPhysics189.rules(mc.theWorld) != null ? "on" : "off", mc.isIntegratedServerRunning() ? "running" : "absent");
        started = true;
        guiWas = mc.gameSettings.hideGUI;
        posWas = new double[]{mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ};
        base = new BlockPos(MathHelper.floor_double(mc.thePlayer.posX), 200, MathHelper.floor_double(mc.thePlayer.posZ));
        onServer(mc, player -> {
            World world = player.worldObj;
            world.getWorldInfo().setRaining(false);
            world.getWorldInfo().setThundering(false);
            player.setGameType(WorldSettings.GameType.CREATIVE);
            inventoryWas = new ItemStack[player.inventory.mainInventory.length];
            for (int i = 0; i < inventoryWas.length; i++) inventoryWas[i] = ItemStack.copyItemStack(player.inventory.mainInventory[i]);
            player.inventory.clear();
            for (int x = -6; x <= 16; x++) for (int z = -6; z <= 6; z++) for (int y = -2; y <= 3; y++)
                set(world, base.add(x, y, z), y == 0 ? Blocks.stone.getDefaultState() : Blocks.air.getDefaultState());
            for (int x = 1; x <= 5; x++) for (int z = -6; z <= -2; z++) { // water pool, two deep: x 2..4, z -5..-3
                boolean inside = x >= 2 && x <= 4 && z >= -5 && z <= -3;
                set(world, base.add(x, -1, z), inside ? Blocks.water.getDefaultState() : Blocks.stone.getDefaultState());
                if (inside) { set(world, base.add(x, -2, z), Blocks.stone.getDefaultState()); set(world, base.add(x, 0, z), Blocks.water.getDefaultState()); }
            }
            for (int x = -4; x <= -2; x++) for (int z = -5; z <= -3; z++) { // lava pool, one deep
                set(world, base.add(x, -1, z), Blocks.stone.getDefaultState());
                set(world, base.add(x, 0, z), Blocks.lava.getDefaultState());
            }
            set(world, base.add(0, -1, 4), Blocks.stone.getDefaultState()); // sand falls without it
            set(world, base.add(0, 0, 4), Blocks.sand.getDefaultState());
            set(world, base.add(0, 1, 4), Blocks.cactus.getDefaultState());
            set(world, base.add(0, 2, 4), Blocks.cactus.getDefaultState());
            set(world, base.add(-5, 0, 5), Blocks.planks.getDefaultState());
            set(world, base.add(-5, 0, 3), Blocks.planks.getDefaultState());
            player.fallDistance = 0;
            facts.put("built", true);
        });
        return after(10);
    }

    /** Frames during which the client's timer stands still: module off, on, off again; then the timer runs again. */
    public static final class FrozenFrames {
        private final Minecraft mc;
        private final Object timer;
        private int frame;
        volatile boolean done;
        int offAgain = -1, onChanged = -1;

        FrozenFrames(Minecraft mc) {
            this.mc = mc;
            this.timer = ReflectionHelper.getPrivateValue(Minecraft.class, mc, "timer", "field_71428_T");
        }

        @SubscribeEvent
        public void frame(TickEvent.RenderTickEvent event) {
            if (event.phase != TickEvent.Phase.END || done) return;
            try {
                switch (++frame) {
                    case 1: ((net.minecraft.util.Timer) timer).timerSpeed = 0; mc.gameSettings.hideGUI = true; module().setEnabled(false); break;
                    case 6: screenshot(mc, "170-ip-rest-off-a"); module().setEnabled(true); break;
                    case 11: screenshot(mc, "170-ip-rest-on-frozen"); module().setEnabled(false); break;
                    case 16:
                        screenshot(mc, "170-ip-rest-off-b");
                        File folder = new File(mc.mcDataDir, "lads-qa/screenshots");
                        BufferedImage a = ImageIO.read(new File(folder, "170-ip-rest-off-a.png")), on = ImageIO.read(new File(folder, "170-ip-rest-on-frozen.png")),
                            b = ImageIO.read(new File(folder, "170-ip-rest-off-b.png"));
                        offAgain = differing(a, b);
                        onChanged = differing(a, on);
                        finish();
                        break;
                    default: break;
                }
            } catch (Exception failure) {
                LogManager.getLogger("TheLadsCore").error("Lads 1.8.9 item physics frozen frames failed", failure);
                finish();
            }
        }

        private void finish() {
            ((net.minecraft.util.Timer) timer).timerSpeed = 1;
            mc.gameSettings.hideGUI = guiWas;
            done = true;
        }

        private static int differing(BufferedImage a, BufferedImage b) {
            if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) return Integer.MAX_VALUE;
            int count = 0;
            for (int y = 0; y < a.getHeight(); y++) for (int x = 0; x < a.getWidth(); x++) if (a.getRGB(x, y) != b.getRGB(x, y)) count++;
            return count;
        }
    }

    /** The player, flying, at (x, y, z) above the arena floor looking with this yaw and pitch. */
    private static void view(Minecraft mc, final double x, final double y, final double z, final float yaw, final float pitch) {
        mc.thePlayer.motionX = mc.thePlayer.motionY = mc.thePlayer.motionZ = 0;
        mc.thePlayer.capabilities.isFlying = true;
        onServer(mc, player -> {
            player.capabilities.isFlying = true;
            player.sendPlayerAbilities();
            player.fallDistance = 0;
            player.playerNetServerHandler.setPlayerLocation(base.getX() + x, base.getY() + y, base.getZ() + z, yaw, pitch);
        });
    }

    private static EntityItem spawn(World world, Item item, int count, double x, double y, double z) {
        EntityItem entity = new EntityItem(world, base.getX() + x, base.getY() + y, base.getZ() + z, new ItemStack(item, count));
        entity.motionX = entity.motionY = entity.motionZ = 0;
        entity.setInfinitePickupDelay();
        world.spawnEntityInWorld(entity);
        spawned.add(entity.getEntityId());
        return entity;
    }

    private static void toss(EntityItem item, double dx, double dy, double dz) {
        item.motionX = dx;
        item.motionY = dy;
        item.motionZ = dz;
        item.velocityChanged = true;
    }

    /** Server thread: where the item is (relative to the arena floor) and whether it is alive. */
    private static void record(World world, String name, int id) {
        Entity item = world.getEntityByID(id);
        facts.put(name, item == null ? new double[]{0, 0, 0, 0}
            : new double[]{item.posX - base.getX(), item.posY - base.getY(), item.posZ - base.getZ(), item.isDead ? 0 : 1});
    }

    /** The server thread has recorded these facts (its tasks can lag behind the client's ticks). */
    private static boolean ready(String... names) {
        for (String name : names) if (!facts.containsKey(name)) return false;
        return true;
    }

    private static double[] fact(String name) {
        double[] value = (double[]) facts.get(name);
        if (value == null) throw new IllegalStateException("no record of " + name);
        return value;
    }

    /** Server thread: how far east the newest cobblestone landed from the thrower, then it goes. */
    private static void thrown(Minecraft mc, final String name) {
        onServer(mc, player -> {
            EntityItem newest = null;
            for (EntityItem item : player.worldObj.getEntitiesWithinAABB(EntityItem.class, player.getEntityBoundingBox().expand(24, 24, 24)))
                if (item.getEntityItem().getItem() == Item.getItemFromBlock(Blocks.cobblestone) && (newest == null || item.getEntityId() > newest.getEntityId())) newest = item;
            facts.put(name, newest == null ? new double[]{0, 0, 0, 0} : new double[]{newest.posX - player.posX, newest.posY - player.posY, 0, 1});
            LogManager.getLogger("TheLadsCore").info("Lads 1.8.9 item physics {}: landed {} blocks east, on the ground {}", name,
                newest == null ? "none" : fmt(newest.posX - player.posX), newest != null && newest.onGround);
            if (newest != null) newest.setDead();
        });
    }

    private static void clearItems(World world) {
        for (int id : spawned) { Entity item = world.getEntityByID(id); if (item != null) item.setDead(); }
        spawned.clear();
    }

    private static void onServer(Minecraft mc, final ServerTask task) {
        final MinecraftServer server = mc.getIntegratedServer();
        final java.util.UUID id = mc.thePlayer.getUniqueID();
        server.addScheduledTask(() -> {
            EntityPlayerMP player = server.getConfigurationManager().getPlayerByUUID(id);
            if (player != null) task.run(player);
        });
    }

    private static void set(World world, BlockPos pos, IBlockState state) {
        synchronized (blocksWere) { if (!blocksWere.containsKey(pos)) blocksWere.put(pos, world.getBlockState(pos)); }
        world.setBlockState(pos, state, 2);
    }

    private static String fmt(double value) { return String.format(java.util.Locale.ROOT, "%.2f", value); }

    /** Also CoreProbe.finish after a failure: keys up, timer running, module and options, arena, items and player back. */
    static void stop() {
        if (!started) return;
        started = false;
        Minecraft mc = Minecraft.getMinecraft();
        if (frozen != null) { MinecraftForge.EVENT_BUS.unregister(frozen); if (!frozen.done) frozen.finish(); }
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindDrop.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
        mc.gameSettings.hideGUI = guiWas;
        if (mc.currentScreen != null && mc.theWorld != null) mc.displayGuiScreen(null);
        ItemPhysicsModule module = module();
        for (Map.Entry<Option, JsonElement> entry : optionsWere.entrySet()) entry.getKey().load(entry.getValue());
        module.setEnabled(wasEnabled);
        module.setLastModified(modifiedWas);
        if (mc.thePlayer != null && mc.getIntegratedServer() != null) onServer(mc, player -> {
            clearItems(player.worldObj);
            for (EntityItem item : player.worldObj.getEntitiesWithinAABB(EntityItem.class, new net.minecraft.util.AxisAlignedBB(base, base).expand(24, 24, 24))) item.setDead();
            synchronized (blocksWere) { // reversed: cactus and pools before the floor under them
                List<Map.Entry<BlockPos, IBlockState>> found = new ArrayList<Map.Entry<BlockPos, IBlockState>>(blocksWere.entrySet());
                java.util.Collections.reverse(found);
                for (Map.Entry<BlockPos, IBlockState> entry : found) player.worldObj.setBlockState(entry.getKey(), entry.getValue(), 2);
                blocksWere.clear();
            }
            player.inventory.clear();
            if (inventoryWas != null) for (int i = 0; i < inventoryWas.length; i++) player.inventory.setInventorySlotContents(i, inventoryWas[i]);
            player.capabilities.isFlying = false;
            player.sendPlayerAbilities();
            player.fallDistance = 0;
            if (posWas != null) player.playerNetServerHandler.setPlayerLocation(posWas[0], posWas[1], posWas[2], 0, 0);
        });
    }
}
