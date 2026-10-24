package com.thelads.core.v1_8_9.feature;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.ItemModelMesher;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

/**
 * QA only, LADS_VERIFY_189_ONLY=cit: Custom Item Textures under Ionium with the sandbox's generated CIT pack (LADS_VERIFY_RESOURCEPACKS):
 * its nbt.display.Name=ipattern:*lads* rule turns a diamond sword solid magenta, its enchantment rule draws its own glint. A sword renamed
 * "Lads QA Sword", one with Sharpness V and a plain one go in the hotbar (the renamed one also in the inventory); magenta pixels are
 * counted in the hotbar and inventory frames. Frames: lads-qa/screenshots/cit-*.png.
 */
final class ProbeCit189 {
    static final List<CoreProbe.Step> STEPS = Arrays.<CoreProbe.Step>asList(ProbeCit189::give, ProbeCit189::hotbar,
        ProbeCit189::inventory, ProbeCit189::inventoryShot, ProbeCit189::inventoryGlint);
    private static final String NAME = "Lads QA Sword";
    private static int glintFrames;

    private ProbeCit189() {}

    private static boolean give(Minecraft mc) {
        java.util.UUID id = mc.thePlayer.getUniqueID();
        mc.getIntegratedServer().addScheduledTask(() -> {
            EntityPlayerMP p = mc.getIntegratedServer().getConfigurationManager().getPlayerByUUID(id);
            Arrays.fill(p.inventory.mainInventory, null);
            p.inventory.mainInventory[0] = named();
            ItemStack enchanted = new ItemStack(Items.diamond_sword);
            enchanted.addEnchantment(Enchantment.sharpness, 5);
            p.inventory.mainInventory[1] = enchanted;
            p.inventory.mainInventory[2] = new ItemStack(Items.diamond_sword);
            p.inventory.mainInventory[9] = named();
            p.worldObj.setWorldTime(6000);
            p.setGameType(net.minecraft.world.WorldSettings.GameType.SURVIVAL); // the survival inventory (creative opens its own screen)
            // ...in peace: the self-test leaves zombies in the QA world, and a death would drop the swords
            net.minecraft.server.MinecraftServer.getServer().setDifficultyForAllWorlds(net.minecraft.world.EnumDifficulty.PEACEFUL);
            p.capabilities.disableDamage = true;
            p.sendPlayerAbilities();
        });
        mc.thePlayer.inventory.currentItem = 0;
        mc.thePlayer.rotationPitch = 0F;
        mc.gameSettings.thirdPersonView = 0;
        return CoreProbe.after(60);
    }

    private static ItemStack named() {
        ItemStack stack = new ItemStack(Items.diamond_sword);
        stack.setStackDisplayName(NAME);
        return stack;
    }

    private static boolean hotbar(Minecraft mc) throws Exception {
        ItemStack[] inv = mc.thePlayer.inventory.mainInventory;
        CoreProbe.check(inv[0] != null && NAME.equals(inv[0].getDisplayName()) && inv[1] != null && inv[1].isItemEnchanted() && inv[2] != null,
            "cit: the renamed, the enchanted and the plain diamond sword reached the hotbar");
        ItemModelMesher mesher = mc.getRenderItem().getItemModelMesher();
        CoreProbe.check(mesher.getItemModel(inv[0]) != mesher.getItemModel(inv[2]),
            "cit: the renamed sword gets the pack's model (nbt.display.Name=ipattern:*lads*), the plain one keeps vanilla's");
        BufferedImage shot = shoot(mc, "cit-hotbar");
        ScaledResolution res = new ScaledResolution(mc);
        int s = res.getScaleFactor(), cx = res.getScaledWidth() / 2, h = res.getScaledHeight();
        // The hotbar's slot i item: x = width / 2 - 88 + 20 i, y = height - 19, 16 x 16 (GuiIngame.renderTooltip).
        double named = magenta(shot, (cx - 88) * s, (h - 19) * s, 16 * s, 16 * s);
        double plain = magenta(shot, (cx - 88 + 40) * s, (h - 19) * s, 16 * s, 16 * s);
        CoreProbe.check(named > 0.5 && plain < 0.02, "cit: hotbar slot 1 is the pack's magenta sword (" + percent(named)
            + " magenta), the plain sword in slot 3 is not (" + percent(plain) + ")");
        // The pack's glint (enchantment rule: cyan stripes, blend add) scrolls, so a frame can miss it: logged here, checked below.
        org.apache.logging.log4j.LogManager.getLogger("TheLadsCore").info("Lads cit QA: hotbar Sharpness sword differs from the plain one in {} of its pixels",
            percent(differ(shot, (cx - 88 + 20) * s, (cx - 88 + 40) * s, (h - 19) * s, 16 * s)));
        return CoreProbe.after(10);
    }

    private static boolean inventory(Minecraft mc) {
        mc.displayGuiScreen(new GuiInventory(mc.thePlayer));
        return CoreProbe.after(30);
    }

    private static boolean inventoryShot(Minecraft mc) throws Exception {
        CoreProbe.check(mc.currentScreen instanceof GuiInventory, "cit: the inventory is open (" + mc.currentScreen + ")");
        BufferedImage shot = shoot(mc, "cit-inventory");
        ScaledResolution res = new ScaledResolution(mc);
        int s = res.getScaleFactor(), left = (res.getScaledWidth() - 176) / 2, top = (res.getScaledHeight() - 166) / 2;
        // GuiInventory (176 x 166): main row 1 slot 0 (inventory slot 9) at (8, 84), hotbar slot 0 at (8, 142).
        double row = magenta(shot, (left + 8) * s, (top + 84) * s, 16 * s, 16 * s), bar = magenta(shot, (left + 8) * s, (top + 142) * s, 16 * s, 16 * s);
        CoreProbe.check(row > 0.5 && bar > 0.5, "cit: the renamed swords in the inventory are magenta too (row " + percent(row) + ", hotbar "
            + percent(bar) + ")");
        return CoreProbe.after(2);
    }

    /** The Sharpness sword (inventory hotbar slot 2) against the plain one (slot 3), frame after frame until the glint shows. */
    private static boolean inventoryGlint(Minecraft mc) throws Exception {
        BufferedImage shot = shoot(mc, "cit-inventory-glint");
        ScaledResolution res = new ScaledResolution(mc);
        int s = res.getScaleFactor(), left = (res.getScaledWidth() - 176) / 2, top = (res.getScaledHeight() - 166) / 2;
        double glint = differ(shot, (left + 26) * s, (left + 44) * s, (top + 142) * s, 16 * s);
        if (glint < 0.05 && ++glintFrames < 12) return CoreProbe.retry(3);
        CoreProbe.check(glint >= 0.05, "cit: the Sharpness sword shows the pack's glint in the inventory (" + percent(glint)
            + " of its pixels differ from the plain sword's, frame " + (glintFrames + 1) + ")");
        mc.thePlayer.closeScreen();
        mc.displayGuiScreen(null);
        return CoreProbe.after(10);
    }

    /** The share of pixels that differ (sum of channel differences over 30) between two size x size squares on one row. */
    private static double differ(BufferedImage image, int x1, int x2, int y, int size) {
        int hits = 0;
        for (int dy = 0; dy < size; dy++)
            for (int dx = 0; dx < size; dx++) {
                int a = image.getRGB(x1 + dx, y + dy), b = image.getRGB(x2 + dx, y + dy);
                int d = Math.abs((a >> 16 & 255) - (b >> 16 & 255)) + Math.abs((a >> 8 & 255) - (b >> 8 & 255)) + Math.abs((a & 255) - (b & 255));
                if (d > 30) hits++;
            }
        return (double) hits / (size * size);
    }

    /** This frame, read back once written (an older run's file of that name removed first). */
    private static BufferedImage shoot(Minecraft mc, String name) throws Exception {
        new java.io.File(mc.mcDataDir, "lads-qa/screenshots/" + name + ".png").delete();
        CoreProbe.screenshot(mc, name);
        return CoreProbe.readShot(mc, name);
    }

    /** The share of near-magenta pixels in a screen rectangle (window pixels, y down). */
    private static double magenta(BufferedImage image, int x, int y, int w, int h) {
        int hits = 0, all = 0;
        for (int py = Math.max(0, y); py < Math.min(image.getHeight(), y + h); py++)
            for (int px = Math.max(0, x); px < Math.min(image.getWidth(), x + w); px++) {
                int rgb = image.getRGB(px, py), r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
                all++;
                if (r > 180 && g < 90 && b > 180) hits++;
            }
        return all == 0 ? 0 : (double) hits / all;
    }

    private static String percent(double share) { return Math.round(share * 100) + "%"; }
}
