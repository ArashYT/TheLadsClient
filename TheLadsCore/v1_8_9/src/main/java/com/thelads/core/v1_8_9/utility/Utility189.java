package com.thelads.core.v1_8_9.utility;

import com.thelads.core.client.BreedingTimer;
import com.thelads.core.client.EnderChestPeek;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.modules.SurvivalGuardsModule;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

/**
 * Parity 1.9.19 utility rows on 1.8.9: Survival Guards (GuardsMixin189 on PlayerControllerMP; 1.8.9 has no stripping or amethyst),
 * Breeding Timer (AnimalMixin189 sees an animal's synced age sign turn to 1 when it breeds; drawn here after the world), Ender Chest Peek
 * (EnderWindowMixin189 marks the ender chest window the server opened; kept here when closed and listed in the item's tooltip), Pack
 * Profiles (GuiScreenResourcePacksMixin's Profiles button, PackProfilesScreen189) and Draggable Server List (ServerListDragMixin189).
 */
public final class Utility189 {
    /** Most item lines an ender chest tooltip lists before "and N more". */
    private static final int LISTED = 9;
    private static final List<EntityAnimal> COOLING = new ArrayList<EntityAnimal>();
    private static NetHandlerPlayClient connection;
    private static Container enderWindow;
    /** What the ender chest held (27 slots, nulls for empty) when last closed on this server, or null. */
    private static ItemStack[] ender;
    private static List<String> enderLines;

    private Utility189() {}

    public static void register() {
        ModuleSupport.registerBuiltIn(BreedingTimer.NAME, EnderChestPeek.NAME, com.thelads.core.client.ServerListDrag.NAME);
        ModuleSupport.registerBuiltInLimited(SurvivalGuardsModule.NAME,
            "Minecraft 1.8.9 has no log stripping or amethyst (added in 1.13 and 1.17), so only Anti Tool Break applies here.");
        SurvivalGuardsModule guards = SurvivalGuardsModule.get();
        if (guards != null) guards.hideHere(guards.antiStripLog.getName(), guards.antiAmethyst.getName());
        MinecraftForge.EVENT_BUS.register(new Utility189());
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        BreedingTimer.tick();
        EnderChestPeek.tick();
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getNetHandler() != connection) { // left or joined a server: forget its ender chest
            connection = mc.getNetHandler();
            ender = null;
            enderLines = null;
            enderWindow = null;
        }
        EntityPlayerSP player = mc.thePlayer;
        if (enderWindow != null && (player == null || player.openContainer != enderWindow)) { // closed: keep what it held
            IInventory chest = ((ContainerChest) enderWindow).getLowerChestInventory();
            ItemStack[] items = new ItemStack[27];
            for (int slot = 0; slot < Math.min(27, chest.getSizeInventory()); slot++) items[slot] = ItemStack.copyItemStack(chest.getStackInSlot(slot));
            ender = items;
            enderLines = null;
            enderWindow = null;
        }
    }

    /** The server opened its ender chest window for the player (S2DPacketOpenWindow titled container.enderchest). */
    public static void enderOpened(Container window) {
        if (EnderChestPeek.on && window instanceof ContainerChest) enderWindow = window;
    }

    @SubscribeEvent
    public void tooltip(ItemTooltipEvent event) {
        if (!EnderChestPeek.on || ender == null || event.itemStack.getItem() != Item.getItemFromBlock(Blocks.ender_chest)) return;
        if (enderLines == null) enderLines = enderLines(ender);
        event.toolTip.addAll(enderLines);
    }

    /** "Ender chest: 12/27 slots", then each stack ("64 x Diamond"), as vanilla lists a shulker box's items. */
    private static List<String> enderLines(ItemStack[] items) {
        List<String> lines = new ArrayList<String>();
        int used = 0, listed = 0;
        for (ItemStack stack : items) if (stack != null) used++;
        lines.add("§7Ender chest: " + used + "/27 slots");
        for (ItemStack stack : items) {
            if (stack == null) continue;
            if (listed++ < LISTED) lines.add("§f" + stack.stackSize + " x " + stack.getDisplayName());
        }
        if (used > LISTED) lines.add("§7§oand " + (used - LISTED) + " more...");
        return lines;
    }

    // Survival Guards: PlayerControllerMP hooks (GuardsMixin189) ask before the click is sent.

    /** Breaking a block or attacking with the held item now would wear it past the limit. */
    public static boolean stopsTool(EntityPlayerSP player) {
        SurvivalGuardsModule guards = SurvivalGuardsModule.get();
        if (guards == null || player == null || player.capabilities.isCreativeMode) return false;
        ItemStack held = player.getCurrentEquippedItem();
        if (held == null || !guards.stopsTool(player.isSneaking(), held.isItemStackDamageable() ? held.getMaxDamage() : 0, held.getItemDamage())) return false;
        Minecraft.getMinecraft().ingameGUI.setRecordPlaying(SurvivalGuardsModule.TOOL_MESSAGE, false);
        return true;
    }

    // Breeding Timer

    /** The animal's synced age sign turned from 0 to 1 (AnimalMixin189): it just bred. */
    public static void bred(EntityAnimal animal) {
        ((BreedingTimer.Tracked) animal).lads$breeding().bredAt(animal.worldObj.getTotalWorldTime());
        if (!COOLING.contains(animal)) COOLING.add(animal);
    }

    /** After the world: each cooling animal's time left, over it like a name tag (hidden behind blocks). */
    @SubscribeEvent
    public void breedingLabels(RenderWorldLastEvent event) {
        if (COOLING.isEmpty()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) {
            COOLING.clear();
            return;
        }
        long now = mc.theWorld.getTotalWorldTime();
        for (int i = COOLING.size() - 1; i >= 0; i--) {
            EntityAnimal animal = COOLING.get(i);
            if (animal.isDead || animal.worldObj != mc.theWorld || !((BreedingTimer.Tracked) animal).lads$breeding().cooling(now)) COOLING.remove(i);
        }
        if (!BreedingTimer.on || COOLING.isEmpty()) return;
        RenderManager view = mc.getRenderManager();
        FontRenderer font = mc.fontRendererObj;
        float partial = event.partialTicks;
        float pitch = (mc.gameSettings.thirdPersonView == 2 ? -1 : 1) * view.playerViewX;
        for (int i = 0; i < COOLING.size(); i++) {
            EntityAnimal animal = COOLING.get(i);
            String text = ((BreedingTimer.Tracked) animal).lads$breeding().label(now);
            GlStateManager.pushMatrix();
            GlStateManager.translate(animal.lastTickPosX + (animal.posX - animal.lastTickPosX) * partial - view.viewerPosX,
                animal.lastTickPosY + (animal.posY - animal.lastTickPosY) * partial + animal.height + (animal.hasCustomName() ? 0.8 : 0.5) - view.viewerPosY,
                animal.lastTickPosZ + (animal.posZ - animal.lastTickPosZ) * partial - view.viewerPosZ);
            GL11.glNormal3f(0, 1, 0);
            GlStateManager.rotate(-view.playerViewY, 0, 1, 0);
            GlStateManager.rotate(pitch, 1, 0, 0);
            GlStateManager.scale(-0.025f, -0.025f, 0.025f);
            GlStateManager.disableLighting();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            font.drawString(text, -font.getStringWidth(text) / 2, 0, 0xFFFF88CC, false);
            GlStateManager.enableLighting();
            GlStateManager.disableBlend();
            GlStateManager.color(1, 1, 1, 1);
            GlStateManager.popMatrix();
        }
    }
}
