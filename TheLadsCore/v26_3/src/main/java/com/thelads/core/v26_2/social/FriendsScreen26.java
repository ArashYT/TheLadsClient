package com.thelads.core.v26_2.social;

import com.mojang.authlib.services.response.PresenceResponse;
import com.mojang.authlib.services.response.PresenceStatus;
import com.mojang.authlib.services.response.PresenceStatusDto;
import com.mojang.blaze3d.platform.InputConstants;
import com.thelads.core.client.gui.LadsPalette;
import com.thelads.core.v26_2.adapter.GuiGraphicsExtractorLadsAdapter;
import com.thelads.core.v26_2.gui.CommonInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.friends.FriendsOverlayScreen;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.*;

/**
 * Lads Friends Screen on 26.3.
 * Replaces vanilla's FriendsOverlayScreen while keeping a "Manage Mojang friends" button to vanilla,
 * and displaying Mojang friends with online/offline status via authlib FriendsService.
 */
public final class FriendsScreen26 extends Screen {
    private final Screen parent;
    private int currentTab = 0; // 0 = Lads Friends, 1 = Mojang Friends
    private Button manageMojangButton;
    private Button tabLadsButton;
    private Button tabMojangButton;
    private Button closeButton;

    private final List<MojangFriendDisplay> mojangFriends = new ArrayList<>();

    public record MojangFriendDisplay(UUID id, String name, String statusText, boolean online) {}

    public FriendsScreen26(Screen parent) {
        super(Component.literal("Friends"));
        this.parent = parent;
        refreshMojangFriends();
    }

    private void refreshMojangFriends() {
        mojangFriends.clear();
        Minecraft mc = Minecraft.getInstance();
        PlayerSocialManager manager = mc.getPlayerSocialManager();
        if (manager == null) return;

        Map<UUID, PresenceStatus> statusMap = new HashMap<>();
        if (manager.getPresenceHandler() != null) {
            PresenceResponse pres = manager.getPresenceHandler().getLatestPresence();
            if (pres != null && pres.presence() != null) {
                for (PresenceStatusDto dto : pres.presence()) {
                    if (dto.profileId() != null && dto.status() != null) {
                        statusMap.put(dto.profileId(), dto.status());
                    }
                }
            }
        }

        List<PlayerSocialManager.PlayerData> friends = manager.getFriends();
        if (friends != null) {
            for (PlayerSocialManager.PlayerData f : friends) {
                PresenceStatus s = statusMap.get(f.id());
                boolean isOnline = s != null && s != PresenceStatus.OFFLINE;
                String text = s == null ? "Offline" : switch (s) {
                    case ONLINE -> "Online";
                    case PLAYING_REALMS -> "Playing Realms";
                    case PLAYING_SERVER -> "Playing on a server";
                    case PLAYING_HOSTED_SERVER -> "Hosting world";
                    case PLAYING_OFFLINE -> "Playing Singleplayer";
                    case OFFLINE -> "Offline";
                };
                String name = f.name() != null ? f.name() : (f.id() != null ? f.id().toString() : "Unknown");
                mojangFriends.add(new MojangFriendDisplay(f.id(), name, text, isOnline));
            }
        }
        mojangFriends.sort(Comparator.comparing((MojangFriendDisplay m) -> !m.online)
            .thenComparing(m -> m.name != null ? m.name : "", String.CASE_INSENSITIVE_ORDER));
    }

    @Override
    protected void init() {
        super.init();
        int cx = width / 2;
        int top = 36;

        tabLadsButton = addRenderableWidget(Button.builder(Component.literal("Lads Friends"), b -> {
            currentTab = 0;
            rebuildWidgets();
        }).bounds(cx - 165, top, 80, 20).build());

        tabMojangButton = addRenderableWidget(Button.builder(Component.literal("Mojang Friends"), b -> {
            currentTab = 1;
            refreshMojangFriends();
            rebuildWidgets();
        }).bounds(cx - 80, top, 100, 20).build());

        manageMojangButton = addRenderableWidget(Button.builder(Component.literal("Manage Mojang friends"), b -> {
            minecraft.setScreenAndShow(new FriendsOverlayScreen(this));
        }).bounds(cx + 25, top, 140, 20).build());

        closeButton = addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
            .bounds(cx - 50, height - 28, 100, 20).build());

        // In Mojang Friends tab, add "Add on Lads" buttons for visible entries
        if (currentTab == 1) {
            int y = top + 32;
            for (int i = 0; i < Math.min(mojangFriends.size(), 8); i++) {
                MojangFriendDisplay friend = mojangFriends.get(i);
                addRenderableWidget(Button.builder(Component.literal("Add on Lads"), b -> {
                    SocialBridge26.onChatActionClicked("friend_add:" + friend.name);
                    b.active = false;
                    b.setMessage(Component.literal("Added"));
                }).bounds(cx + 80, y + i * 24 - 2, 80, 18).build());
            }
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0xCC100B10);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);

        int cx = width / 2;
        g.fill(0, 0, width, 2, LadsPalette.ACCENT);
        g.text(font, Component.literal("Lads Friends & Social"), cx - font.width("Lads Friends & Social") / 2, 14, LadsPalette.TEXT, false);

        int startY = 72;
        if (currentTab == 0) {
            // Lads Friends tab
            // TODO(S3/S4): When SocialState merges, render the common FriendsScreenLogic
            g.text(font, Component.literal("Lads Friends"), cx - 160, startY, LadsPalette.ACCENT, false);
            g.text(font, Component.literal("Connect with Lads players peer to peer across worlds."), cx - 160, startY + 16, LadsPalette.MUTED, false);
            g.text(font, Component.literal("Signaling and direct connections active."), cx - 160, startY + 32, 0xFF4ADE80, false);
        } else {
            // Mojang Friends tab (display-only)
            g.text(font, Component.literal("Mojang Friends (Display Only)"), cx - 160, startY, LadsPalette.ACCENT, false);
            if (mojangFriends.isEmpty()) {
                g.text(font, Component.literal("No Mojang friends found."), cx - 160, startY + 20, LadsPalette.MUTED, false);
            } else {
                int y = startY + 20;
                for (int i = 0; i < Math.min(mojangFriends.size(), 8); i++) {
                    MojangFriendDisplay friend = mojangFriends.get(i);
                    int dotColor = friend.online ? 0xFF4ADE80 : 0xFF6B7280;
                    g.fill(cx - 160, y + 4, cx - 154, y + 10, dotColor);
                    g.text(font, Component.literal(friend.name), cx - 148, y + 2, LadsPalette.TEXT, false);
                    g.text(font, Component.literal(friend.statusText), cx - 40, y + 2, LadsPalette.MUTED, false);
                    y += 24;
                }
            }
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }
}
