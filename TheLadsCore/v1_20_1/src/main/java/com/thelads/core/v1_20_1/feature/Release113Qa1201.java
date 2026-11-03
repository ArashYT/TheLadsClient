package com.thelads.core.v1_20_1.feature;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.client.LadsNetwork;
import com.thelads.core.client.gui.ClientImportQa;
import com.thelads.core.client.gui.LadsSettingsScreen;
import com.thelads.core.client.hypixel.NickDetector;
import com.thelads.core.client.killbanner.KillBanners;
import com.thelads.core.client.social.SocialBridge;
import com.thelads.core.client.social.SocialProtocol;
import com.thelads.core.client.social.SocialState;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.KillBannerModule;
import com.thelads.core.modules.NickDetectorModule;
import com.thelads.core.v1_20_1.gui.LadsSettingsScreen1201;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** QA only: script verbs for the 1.13.0 checks on 1.20.1, none of which touches a public server or an account. */
final class Release113Qa1201 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final UUID NICKED = UUID.fromString("a1b2c3d4-0000-1000-8000-00805f9b34fb"); // version 1, as a Hypixel nick's
    private static final int STAND_IN = Integer.MAX_VALUE - 500;

    private Release113Qa1201() {}

    static void register() {
        QaProbe1201.registerStep("import113", s -> { ClientImportQa.step(menu(), s); return true; });
        QaProbe1201.registerStep("packs113", s -> { packs(); return true; });
        QaProbe1201.registerStep("friends113", s -> { friends(); return true; });
        QaProbe1201.registerStep("nick113", s -> { nick(s); return true; });
        QaProbe1201.registerStep("assist113", Release113Qa1201::assists);
        QaProbe1201.registerStep("guiscale113", s -> { Minecraft mc = Minecraft.getInstance(); if (s.length > 3) org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), Integer.parseInt(s[2]), Integer.parseInt(s[3])); mc.options.guiScale().set(Integer.parseInt(s[1])); mc.resizeDisplay(); return true; });
    }

    private static LadsSettingsScreen menu() { return ((LadsSettingsScreen1201) Minecraft.getInstance().screen).ui(); }

    /** A servers.dat with no stored choice, Disabled and Enabled, read by the game's own list; the title Network button's entry. The file is put back. */
    private static void packs() {
        try {
            Minecraft mc = Minecraft.getInstance();
            Path file = com.thelads.core.shared.SharedContentPaths.serversFile();
            byte[] before = Files.exists(file) ? Files.readAllBytes(file) : null;
            ListTag servers = new ListTag();
            for (String kind : List.of("none", "false", "true")) {
                CompoundTag entry = new CompoundTag();
                entry.putString("name", "QA " + kind);
                entry.putString("ip", "qa-" + kind + ".invalid");
                if (!kind.equals("none")) entry.putBoolean("acceptTextures", kind.equals("true"));
                servers.add(entry);
            }
            CompoundTag root = new CompoundTag();
            root.put("servers", servers);
            NbtIo.write(root, file.toFile());
            ServerList list = new ServerList(mc);
            list.load();
            for (int i = 0; i < list.size(); i++) LOGGER.info("Lads qa packs113: {} = {}", list.get(i).ip, list.get(i).getResourcePackStatus());
            LOGGER.info("Lads qa packs113: Network button entry = {}", new ServerData(LadsNetwork.NAME, LadsNetwork.ADDRESS, false).getResourcePackStatus());
            if (before != null) Files.write(file, before); else Files.delete(file);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Friends as the social server announces them, through its own message path: on the network, on another server, in a menu, offline. */
    private static void friends() {
        friend("LadsQaNetwork", LadsNetwork.ADDRESS, "server");
        friend("LadsQaOtherSrv", null, "server");
        friend("LadsQaMenu", null, "menu");
        friend("LadsQaOffline", null, "offline");
        SocialBridge.get().openFriendsScreen();
    }

    private static void friend(String name, String server, String state) {
        JsonObject presence = new JsonObject();
        presence.addProperty("state", state);
        if (server != null) presence.addProperty("server", server);
        JsonObject message = new JsonObject();
        message.addProperty("t", SocialProtocol.FRIEND_ADDED);
        message.addProperty("uuid", UUID.nameUUIDFromBytes(name.getBytes()).toString());
        message.addProperty("name", name);
        message.addProperty("since", 1L);
        message.add("presence", presence);
        SocialState.get().onMessage(message);
    }

    /** nick113 inject | address host | on | click control | confirm | close | log | clean */
    private static void nick(String[] s) {
        Minecraft mc = Minecraft.getInstance();
        Module module = ModuleManager.getInstance().getModule(NickDetectorModule.NAME);
        switch (s[1]) {
            case "inject" -> {
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                buf.writeEnumSet(EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED), ClientboundPlayerInfoUpdatePacket.Action.class);
                buf.writeVarInt(1);
                buf.writeUUID(NICKED);
                buf.writeUtf("QaNicked", 16);
                buf.writeGameProfileProperties(new GameProfile(NICKED, "QaNicked").getProperties());
                buf.writeBoolean(true);
                mc.getConnection().handlePlayerInfoUpdate(new ClientboundPlayerInfoUpdatePacket(buf));
                RemotePlayer player = new RemotePlayer(mc.level, new GameProfile(NICKED, "QaNicked"));
                player.setId(STAND_IN);
                var ahead = mc.player.getEyePosition().add(mc.player.getLookAngle().multiply(1, 0, 1).normalize().scale(3));
                player.setPos(ahead.x, mc.player.getY(), ahead.z);
                mc.level.addPlayer(STAND_IN, player);
            }
            case "address" -> HypixelSafetyManager.qaAddress = s[2];
            case "on" -> module.setEnabled(true);
            case "click" -> {
                var rect = menu().controlBounds(s[2]);
                if (rect == null) throw new IllegalStateException("not drawn: " + s[2] + " in " + menu().controlIds());
                menu().mouseClicked(rect.x() + rect.width() / 2.0, rect.y() + rect.height() / 2.0, 0);
            }
            case "confirm" -> {
                var rect = menu().confirmDialog().confirmBounds();
                menu().mouseClicked(rect.x() + rect.width() / 2.0, rect.y() + rect.height() / 2.0, 0);
            }
            case "close" -> mc.setScreen(null);
            case "log" -> LOGGER.info("Lads qa nick113: address={} isHypixel={} block={} bypassed={} isEnabled={} markers={} tab={} display='{}' dangerOpen={}",
                HypixelSafetyManager.currentHost(), HypixelSafetyManager.isHypixel(), HypixelSafetyManager.blockOf(NickDetectorModule.NAME), HypixelSafetyManager.isBypassed(NickDetectorModule.NAME),
                module.isEnabled(), NickDetector.markers(), mc.getConnection().getPlayerInfo(NICKED) != null,
                mc.level.getEntity(STAND_IN) == null ? "" : mc.level.getEntity(STAND_IN).getDisplayName().getString(),
                mc.screen instanceof LadsSettingsScreen1201 && menu().confirmDialog().isDanger());
            case "clean" -> {
                HypixelSafetyManager.removeBypass(NickDetectorModule.NAME);
                module.setEnabled(false);
                HypixelSafetyManager.qaAddress = null;
                mc.level.removeEntity(STAND_IN, Entity.RemovalReason.DISCARDED);
            }
            default -> throw new IllegalArgumentException("nick113 " + s[1]);
        }
    }

    /** assists on?, the chat line after the death ("" = silence, ME = the player's name, VICTIM = the stand-in's), banners expected. */
    private record Case(boolean assists, String line, int banners) {}
    private static final List<Case> CASES = List.of(
        new Case(true, "", 1), new Case(true, "+5 coins! (Assist)", 1),
        new Case(false, "", 1), new Case(false, "+5 coins! (Assist)", 0),
        new Case(false, "VICTIM was killed by ME.", 1), new Case(false, "VICTIM was killed by Steve.", 0));
    private static int index = -1, ticks, before, failures;
    private static boolean enabledBefore, assistsBefore;

    /** A real attack on a stand-in, its death by the game's entity event, the line by /tellraw: the module's whole path, assists on and off. */
    private static boolean assists(String[] s) {
        Minecraft mc = Minecraft.getInstance();
        KillBannerModule module = (KillBannerModule) ModuleManager.getInstance().getModule("KillBanner");
        if (index < 0) {
            enabledBefore = module.isEnabled();
            assistsBefore = module.assistBanners.get();
            module.setEnabled(true);
            module.unlimitedStreak.set(true);
            KillBanners.reset();
            index = 0;
            ticks = 0;
            return false;
        }
        if (index >= CASES.size()) {
            module.assistBanners.set(assistsBefore);
            module.setEnabled(enabledBefore);
            index = -1;
            int failed = failures;
            failures = 0;
            LOGGER.info("Lads qa assist113 END: {} failed", failed);
            if (failed > 0) throw new IllegalStateException("assist113: " + failed + " case(s) failed");
            return true;
        }
        Case c = CASES.get(index);
        String victim = "QaAssist" + index;
        int id = STAND_IN + 1 + index;
        switch (ticks++) {
            case 0 -> {
                module.assistBanners.set(c.assists());
                before = KillBanners.TIMELINE.streak();
                RemotePlayer player = new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), victim));
                player.setId(id);
                player.setPos(mc.player.getX(), mc.player.getY(), mc.player.getZ() + 2);
                mc.level.addPlayer(id, player);
                mc.gameMode.attack(mc.player, player);
                mc.getConnection().handleEntityEvent(new ClientboundEntityEventPacket(player, (byte) 3));
                if (!c.line().isEmpty()) {
                    String line = c.line().replace("VICTIM", victim).replace("ME", mc.player.getGameProfile().getName());
                    mc.player.connection.sendCommand("tellraw @s {\"text\":\"" + line + "\"}");
                }
            }
            case 8 -> shot(mc, "assist113/case" + index + "-now");
            case 40 -> { // past the 750 ms grace
                int made = KillBanners.TIMELINE.streak() - before;
                boolean ok = made == c.banners();
                if (!ok) failures++;
                LOGGER.info("Lads qa assist113 {}: assists={} line='{}' banners={} expected={}", ok ? "PASS" : "FAIL", c.assists(), c.line(), made, c.banners());
                shot(mc, "assist113/case" + index + "-after");
                mc.level.removeEntity(id, Entity.RemovalReason.DISCARDED);
            }
            case 110 -> { index++; ticks = 0; } // past the 3 s that pair a reward line with a kill
            default -> {}
        }
        return false;
    }

    private static void shot(Minecraft mc, String name) {
        try { QaProbe1201.shot(mc, name); } catch (Exception e) { LOGGER.error("Lads qa shot failed: {}", e.toString()); }
    }
}
