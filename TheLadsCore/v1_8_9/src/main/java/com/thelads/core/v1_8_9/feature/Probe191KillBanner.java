package com.thelads.core.v1_8_9.feature;

import com.mojang.authlib.GameProfile;
import com.thelads.core.client.killbanner.KillBanner191Qa;
import com.thelads.core.modules.KillBannerModule;
import com.thelads.core.v1_8_9.feature.fastquit.mixin.GuiScreenFastQuitAccessor;
import com.thelads.core.v1_8_9.gui.ModuleVolumeSlider189;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.audio.SoundCategory;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreenOptionsSounds;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;

/**
 * QA only (LADS_VERIFY_189_ONLY=kb191, CoreProbe): the 1.9.1 kill banner run (common KillBanner191Qa) on 1.8.9, in the QA
 * world: Hypixel lines by the integrated server's /tellraw, a real pig kill, the Music &amp; Sounds sliders, the loudness run.
 */
final class Probe191KillBanner implements KillBanner191Qa.Driver {
    private static final int FIRST_ID = -1900;
    private static KillBanner191Qa run;
    private final AtomicInteger pig = new AtomicInteger(Integer.MIN_VALUE);
    private final Map<SoundCategory, Float> volumes = new EnumMap<SoundCategory, Float>(SoundCategory.class);
    private int standIns;
    private double[] home;

    private Probe191KillBanner() {}

    static boolean run(Minecraft mc) {
        if (run == null) run = new KillBanner191Qa(new Probe191KillBanner());
        if (!run.tick()) return CoreProbe.retry(0);
        boolean failed = run.failed();
        run = null;
        if (failed) throw new IllegalStateException("1.8.9 kb191: a check failed (see the 'Lads kb191' lines)");
        return CoreProbe.after(5);
    }

    @Override public String me() { return Minecraft.getMinecraft().thePlayer.getName(); }

    @Override public void standIns(List<String> names) {
        Minecraft mc = Minecraft.getMinecraft();
        for (String name : names) {
            EntityOtherPlayerMP player = new EntityOtherPlayerMP(mc.theWorld, new GameProfile(UUID.randomUUID(), name));
            standIns++;
            player.setPosition(mc.thePlayer.posX + standIns, mc.thePlayer.posY, mc.thePlayer.posZ + 4);
            mc.theWorld.addEntityToWorld(FIRST_ID - standIns, player);
        }
    }

    @Override public void removeStandIns() {
        for (int i = 1; i <= standIns; i++) Minecraft.getMinecraft().theWorld.removeEntityFromWorld(FIRST_ID - i);
        standIns = 0;
        final int id = pig.getAndSet(Integer.MIN_VALUE);
        if (id != Integer.MIN_VALUE) server(new Server() { public void run(MinecraftServer server, EntityPlayerMP sp) {
            Entity entity = sp.worldObj.getEntityByID(id);
            if (entity != null) entity.setDead();
        }});
    }

    @Override public void tellraw(final String json) {
        // The server itself runs it (the QA world's cheats may be off): the real command and S02 chat packet.
        server(new Server() { public void run(MinecraftServer server, EntityPlayerMP sp) {
            server.getCommandManager().executeCommand(server, "/tellraw " + sp.getName() + " " + json);
        }});
    }

    @Override public void spawnPig() {
        server(new Server() { public void run(MinecraftServer server, EntityPlayerMP sp) {
            EntityPig mob = new EntityPig(sp.worldObj);
            mob.setLocationAndAngles(sp.posX, sp.posY, sp.posZ + 1.5, 0, 0);
            mob.setNoAI(true);
            sp.worldObj.spawnEntityInWorld(mob);
            mob.setHealth(.5F);
            pig.set(mob.getEntityId());
        }});
    }

    @Override public boolean attackPig() {
        Minecraft mc = Minecraft.getMinecraft();
        Entity target = pig.get() == Integer.MIN_VALUE ? null : mc.theWorld.getEntityByID(pig.get());
        if (target == null) return false;
        mc.playerController.attackEntity(mc.thePlayer, target); // the player's own blow (Forge's attack event, KillBanner189)
        return true;
    }

    @Override public void openSoundScreen() {
        Minecraft mc = Minecraft.getMinecraft();
        mc.displayGuiScreen(new GuiScreenOptionsSounds(null, mc.gameSettings));
    }

    @Override public boolean clickSlider(String label, double fraction) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen == null) return false;
        for (GuiButton button : ((GuiScreenFastQuitAccessor) mc.currentScreen).lads$buttons()) {
            if (!(button instanceof ModuleVolumeSlider189) || !button.displayString.startsWith(label)) continue;
            int x = button.xPosition + 4 + (int) Math.round(fraction * (button.width - 8)), y = button.yPosition + 10;
            // What GuiScreen.mouseClicked and mouseReleased do with a button under the pointer.
            boolean pressed = button.mousePressed(mc, x, y);
            button.mouseReleased(x, y);
            LogManager.getLogger("TheLadsCore").info("Lads kb191 slider '{}' at ({}, {}): pressed {}, now '{}'", label, x, y, pressed, button.displayString);
            return pressed;
        }
        return false;
    }

    @Override public void closeScreen() { Minecraft.getMinecraft().displayGuiScreen(null); }

    @Override public void shot(String name) { CoreProbe.screenshot(Minecraft.getMinecraft(), name); }

    @Override public List<String> references() {
        return Arrays.asList("game.player.hurt 1", "random.successful_hit 1", "random.levelup 0.75", "note.pling 1");
    }

    @Override public void playReference(String reference) {
        Minecraft mc = Minecraft.getMinecraft();
        String[] parts = reference.split(" ");
        mc.getSoundHandler().playSound(new PositionedSoundRecord(new ResourceLocation(parts[0]), Float.parseFloat(parts[1]), 1.0F,
            (float) mc.thePlayer.posX, (float) (mc.thePlayer.posY + mc.thePlayer.getEyeHeight()), (float) mc.thePlayer.posZ));
    }

    @Override public void banner(KillBannerModule.Pick pick) {
        KillBanner189.bindCurrent();
        KillBanner189.trigger(1, false, false, pick);
    }

    @Override public void mute(boolean on) {
        Minecraft mc = Minecraft.getMinecraft();
        if (on) {
            for (SoundCategory category : SoundCategory.values()) {
                if (category == SoundCategory.MASTER || category == SoundCategory.PLAYERS || category == SoundCategory.RECORDS) continue; // note.pling is a record sound
                volumes.put(category, mc.gameSettings.getSoundLevel(category));
                mc.gameSettings.setSoundLevel(category, 0);
            }
        } else {
            for (Map.Entry<SoundCategory, Float> volume : volumes.entrySet()) mc.gameSettings.setSoundLevel(volume.getKey(), volume.getValue());
            volumes.clear();
        }
    }

    /** The banner's sound comes from where the game puts it: the world's origin, for a player standing 1000 blocks east facing south. */
    @Override public boolean farFromOrigin(boolean on) {
        final Minecraft mc = Minecraft.getMinecraft();
        if (on) home = new double[] {mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ};
        if (home == null) return false;
        final double x = on ? 1000.5 : home[0], y = home[1], z = on ? .5 : home[2]; // superflat: the same ground everywhere
        if (!on) home = null;
        server(new Server() { public void run(MinecraftServer server, EntityPlayerMP sp) {
            sp.playerNetServerHandler.setPlayerLocation(x, y, z, 0, 0);
        }});
        return true;
    }

    private interface Server { void run(MinecraftServer server, EntityPlayerMP player); }

    private static final AtomicInteger PENDING = new AtomicInteger();

    @Override public boolean serverCaughtUp() { return PENDING.get() == 0; }

    private static void server(final Server action) {
        final MinecraftServer server = Minecraft.getMinecraft().getIntegratedServer();
        final UUID id = Minecraft.getMinecraft().thePlayer.getUniqueID();
        PENDING.incrementAndGet();
        server.addScheduledTask(new Runnable() { public void run() {
            try {
                EntityPlayerMP player = server.getConfigurationManager().getPlayerByUUID(id);
                if (player != null) action.run(server, player);
            } finally {
                PENDING.decrementAndGet();
            }
        }});
    }
}
