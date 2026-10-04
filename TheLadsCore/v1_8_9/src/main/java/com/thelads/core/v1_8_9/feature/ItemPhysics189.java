package com.thelads.core.v1_8_9.feature;

import com.thelads.core.client.ItemPhysics;
import com.thelads.core.modules.ItemPhysicsModule;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;

/**
 * Item Physics on 1.8.9 (26.x: NativeItemPhysics). The look is client-side and works on any server; the rules run only on this
 * client's integrated server (this mod is client-only, so a world here is either that server's or a client world) and, for the
 * same physics on both ends, on this client's own worlds while that server runs. On a remote server every rule is inert.
 */
public final class ItemPhysics189 {
    static final int BURNS = 1, FLOATS = 2;
    private static final Map<Item, Integer> KINDS = new ConcurrentHashMap<Item, Integer>();
    private static final ItemPhysics.Charge CHARGE = new ItemPhysics.Charge();
    private static volatile UUID thrower;
    private static volatile float power;
    /** Server thread: our right-click pickup is touching the item, so the no-auto-pickup rule lets it through. */
    private static boolean picking;

    public static ItemPhysicsModule module() {
        Object module = Options189.module(ItemPhysicsModule.NAME);
        return module instanceof ItemPhysicsModule ? (ItemPhysicsModule) module : null;
    }

    /** Dropped items lie flat and tumble (any server: it only changes how this client draws them). */
    public static boolean renders() {
        ItemPhysicsModule module = module();
        return module != null && module.isEnabled() && module.animation.get();
    }

    /** The module, when its singleplayer rules apply in this world: the integrated server's, or this client's while it runs. */
    public static ItemPhysicsModule rules(World world) {
        ItemPhysicsModule module = module();
        if (module == null || !module.isEnabled()) return null;
        boolean owned = world.isRemote ? Minecraft.getMinecraft().isIntegratedServerRunning() : MinecraftServer.getServer() instanceof IntegratedServer;
        return owned ? module : null;
    }

    public static boolean burns(ItemStack stack) { return stack != null && (kind(stack) & BURNS) != 0; }

    public static boolean floats(ItemStack stack) { return stack != null && (kind(stack) & FLOATS) != 0; }

    private static int kind(ItemStack stack) {
        Integer known = KINDS.get(stack.getItem());
        if (known != null) return known;
        Block block = Block.getBlockFromItem(stack.getItem());
        boolean fuel = TileEntityFurnace.isItemFuel(stack), flammable = block != null && block != Blocks.air && block.getMaterial().getCanBurn();
        Object name = Item.itemRegistry.getNameForObject(stack.getItem());
        String id = name == null ? "" : ((net.minecraft.util.ResourceLocation) name).getResourcePath();
        int kind = (ItemPhysics.burns(id, fuel, flammable) ? BURNS : 0) | (ItemPhysics.floats(id, fuel, flammable) ? FLOATS : 0);
        KINDS.put(stack.getItem(), kind);
        return kind;
    }

    /** How deep the item's bottom is in this liquid (water or lava): 0 out of it, 1 once a whole block of it is above. */
    public static double depth(EntityItem item, net.minecraft.block.material.Material liquid) {
        BlockPos at = new BlockPos(item.posX, item.posY, item.posZ);
        IBlockState state = item.worldObj.getBlockState(at);
        if (state.getBlock().getMaterial() != liquid) return 0;
        if (item.worldObj.getBlockState(at.up()).getBlock().getMaterial() == liquid) return 1;
        return Math.max(0, at.getY() + 1 - BlockLiquid.getLiquidHeightPercent(state.getValue(BlockLiquid.LEVEL)) - item.posY);
    }

    /** A burning flammable item lying on a flammable block sets it alight, if the doFireTick game rule allows fire to spread. */
    public static void ignite(EntityItem item) {
        if (item.ticksExisted % 10 != 0 || !item.isBurning() || !item.onGround || !burns(item.getEntityItem())) return;
        ItemPhysicsModule module = rules(item.worldObj);
        BlockPos at = new BlockPos(item);
        if (module != null && module.ignite.get() && item.worldObj.getGameRules().getBoolean("doFireTick") && item.worldObj.isAirBlock(at)
            && item.worldObj.getBlockState(at.down()).getBlock().getMaterial().getCanBurn())
            item.worldObj.setBlockState(at, Blocks.fire.getDefaultState());
    }

    /** Despawn time: Forge's per-item lifespan, set where vanilla's 5 minutes would apply (the integrated server only). */
    @SubscribeEvent
    public void joined(EntityJoinWorldEvent event) {
        if (event.world.isRemote && event.entity == Minecraft.getMinecraft().thePlayer && renders())
            LogManager.getLogger("TheLadsCore-1.8.9").info("Lads Item Physics: {}", Minecraft.getMinecraft().isIntegratedServerRunning()
                ? "singleplayer rules on (this client's integrated server)" : "look only; the remote server keeps its own item rules");
        if (event.world.isRemote || !(event.entity instanceof EntityItem)) return;
        ItemPhysicsModule module = rules(event.world);
        EntityItem item = (EntityItem) event.entity;
        if (module != null && item.lifespan == 6000) item.lifespan = ItemPhysics.despawnTicks(module.despawn.getValue());
    }

    /** Auto-pickup stays off for the host while right-click pickup is on (LAN guests cannot right-click, so they keep it). */
    @SubscribeEvent
    public void pickup(EntityItemPickupEvent event) {
        EntityPlayer player = event.entityPlayer;
        if (picking || player.worldObj.isRemote) return;
        ItemPhysicsModule module = rules(player.worldObj);
        if (module != null && module.pickup.get() && player.getName().equals(MinecraftServer.getServer().getServerOwner())) event.setCanceled(true);
    }

    /** Minecraft.rightClickMouse: right-click on a dropped item in reach picks it up; true when one was picked. */
    public static boolean pickUp(Minecraft mc) {
        ItemPhysicsModule module = module();
        final IntegratedServer server = mc.getIntegratedServer();
        if (module == null || !module.isEnabled() || !module.pickup.get() || server == null || mc.thePlayer == null || mc.thePlayer.isSpectator()) return false;
        Vec3 eye = mc.thePlayer.getPositionEyes(1), look = mc.thePlayer.getLook(1);
        double reach = mc.playerController.getBlockReachDistance();
        Vec3 end = eye.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach);
        MovingObjectPosition block = mc.objectMouseOver;
        double nearest = block != null && block.typeOfHit != MovingObjectPosition.MovingObjectType.MISS && block.hitVec != null
            ? eye.squareDistanceTo(block.hitVec) : Double.MAX_VALUE;
        EntityItem target = null;
        for (EntityItem item : mc.theWorld.getEntitiesWithinAABB(EntityItem.class, mc.thePlayer.getEntityBoundingBox().expand(reach + 1, reach + 1, reach + 1))) {
            MovingObjectPosition hit = item.isDead ? null : item.getEntityBoundingBox().expand(0.15, 0.15, 0.15).calculateIntercept(eye, end);
            if (hit != null && eye.squareDistanceTo(hit.hitVec) < nearest) {
                nearest = eye.squareDistanceTo(hit.hitVec);
                target = item;
            }
        }
        if (target == null) return false;
        final int id = target.getEntityId();
        final UUID uuid = mc.thePlayer.getUniqueID();
        server.addScheduledTask(new Runnable() {
            @Override public void run() {
                EntityPlayerMP host = server.getConfigurationManager().getPlayerByUUID(uuid);
                Entity item = host == null ? null : host.worldObj.getEntityByID(id);
                if (!(item instanceof EntityItem)) return;
                picking = true;
                try { item.onCollideWithPlayer(host); } finally { picking = false; }
            }
        });
        mc.thePlayer.swingItem();
        return true;
    }

    /** Start of each client tick: the drop key charges while held and throws on release (the integrated server only). */
    @SubscribeEvent
    public void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getMinecraft();
        ItemPhysicsModule module = module();
        if (module == null || !module.isEnabled() || !module.charged.get() || !mc.isIntegratedServerRunning() || mc.thePlayer == null
            || mc.currentScreen != null || mc.thePlayer.isSpectator()) {
            CHARGE.reset();
            return;
        }
        boolean clicked = false;
        while (mc.gameSettings.keyBindDrop.isPressed()) clicked = true;
        float throwPower = CHARGE.tick(mc.gameSettings.keyBindDrop.isKeyDown(), clicked);
        if (throwPower <= 0 || mc.thePlayer.getHeldItem() == null) return;
        thrower = mc.thePlayer.getUniqueID();
        power = throwPower;
        mc.thePlayer.dropOneItem(GuiScreen.isCtrlKeyDown());
    }

    /** The host's charged throw leaves the hand faster (Forge posts this before the item enters the world). */
    @SubscribeEvent
    public void tossed(ItemTossEvent event) {
        if (event.player.worldObj.isRemote || !event.player.getUniqueID().equals(thrower)) return;
        thrower = null;
        event.entityItem.motionX *= power;
        event.entityItem.motionY *= power;
        event.entityItem.motionZ *= power;
    }

    /** The charge, as a thin bar under the crosshair (after the whole HUD: a Lads crosshair replaces vanilla's). */
    @SubscribeEvent
    public void overlay(RenderGameOverlayEvent.Post event) {
        float shown = CHARGE.shown();
        if (event.type != RenderGameOverlayEvent.ElementType.ALL || shown <= 0) return;
        int x = event.resolution.getScaledWidth() / 2 - 8, y = event.resolution.getScaledHeight() / 2 + 9;
        Gui.drawRect(x, y, x + 16, y + 2, 0x80000000);
        Gui.drawRect(x, y, x + Math.round(16 * shown), y + 2, 0xE0FFFFFF);
    }
}
