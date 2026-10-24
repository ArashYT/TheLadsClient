package com.thelads.core.v26_2.social;

import com.mojang.authlib.services.response.PresenceResponse;
import com.mojang.authlib.services.response.PresenceStatus;
import com.mojang.authlib.services.response.PresenceStatusDto;
import com.mojang.blaze3d.platform.InputConstants;
import com.thelads.core.client.gui.LadsPalette;
import com.thelads.core.client.social.SocialState;
import com.thelads.core.client.social.ui.FriendsScreenLogic;
import com.thelads.core.v26_2.adapter.GuiGraphicsExtractorLadsAdapter;
import com.thelads.core.v26_2.gui.CommonInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.friends.FriendsOverlayScreen;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.*;

/**
 * Lads Friends screen on 26.3 (replaces vanilla's FriendsOverlayScreen, same O key): the common FriendsScreenLogic, plus a
 * display-only Mojang friends view (authlib FriendsService presence) with "Add on Lads" and "Manage Mojang friends" (vanilla's screen).
 */
public final class FriendsScreen26 extends Screen {
    private final Screen parent;
    private final FriendsScreenLogic ui;
    private boolean mojangView;

    private final List<MojangFriendDisplay> mojangFriends = new ArrayList<>();

    public record MojangFriendDisplay(UUID id, String name, String statusText, boolean online) {}

    public FriendsScreen26(Screen parent) {
        super(Component.literal("Friends"));
        this.parent = parent;
        this.ui = FriendsScreenLogic.social(() -> Minecraft.getInstance().setScreenAndShow(parent));
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

    private void showMojang(boolean show) {
        mojangView = show;
        if (show) refreshMojangFriends();
        rebuildWidgets();
    }

    @Override
    protected void init() {
        super.init();
        int cx = width / 2;
        if (!mojangView) {
            int panelW = Math.min(width - 32, 540); // FriendsScreenLogic's panel: its footer's left side is free
            addRenderableWidget(Button.builder(Component.literal("Mojang friends"), b -> showMojang(true))
                .bounds((width - panelW) / 2, height - 28, 110, 20).build());
            return;
        }
        int top = 36;
        addRenderableWidget(Button.builder(Component.literal("Lads friends"), b -> showMojang(false)).bounds(cx - 165, top, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Manage Mojang friends"), b ->
            minecraft.setScreenAndShow(new FriendsOverlayScreen(this))).bounds(cx + 25, top, 140, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(cx - 50, height - 28, 100, 20).build());

        int y = top + 32;
        for (int i = 0; i < Math.min(mojangFriends.size(), 8); i++) {
            MojangFriendDisplay friend = mojangFriends.get(i);
            addRenderableWidget(Button.builder(Component.literal("Add on Lads"), b -> {
                SocialState.get().addFriend(friend.name);
                b.active = false;
                b.setMessage(Component.literal("Requested"));
            }).bounds(cx + 80, y + i * 24 - 2, 80, 18).build());
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        if (mojangView) g.fill(0, 0, width, height, 0xCC100B10); // the Lads view: FriendsScreenLogic paints its own background
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        if (!mojangView) {
            ui.render(new GuiGraphicsExtractorLadsAdapter(g, font), mx, my);
            super.extractRenderState(g, mx, my, delta);
            return;
        }
        super.extractRenderState(g, mx, my, delta);

        int cx = width / 2;
        g.fill(0, 0, width, 2, LadsPalette.ACCENT);
        g.text(font, Component.literal("Mojang Friends (Display Only)"), cx - font.width("Mojang Friends (Display Only)") / 2, 14, LadsPalette.TEXT, false);
        int y = 68;
        if (mojangFriends.isEmpty()) {
            g.text(font, Component.literal("No Mojang friends found."), cx - 160, y, LadsPalette.MUTED, false);
            return;
        }
        for (int i = 0; i < Math.min(mojangFriends.size(), 8); i++) {
            MojangFriendDisplay friend = mojangFriends.get(i);
            g.fill(cx - 160, y + 4, cx - 154, y + 10, friend.online ? 0xFF4ADE80 : 0xFF6B7280);
            g.text(font, Component.literal(friend.name), cx - 148, y + 2, LadsPalette.TEXT, false);
            g.text(font, Component.literal(friend.statusText), cx - 40, y + 2, LadsPalette.MUTED, false);
            y += 24;
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!mojangView && ui.mouseClicked(event.x(), event.y(), CommonInput.button(event.button()))) return true;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!mojangView && ui.mouseScrolled(mouseX, mouseY, verticalAmount)) return true;
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (mojangView) {
            if (event.key() != InputConstants.KEY_ESCAPE) return super.keyPressed(event);
            showMojang(false);
            return true;
        }
        if (ui.keyPressed(CommonInput.key(event), 0, CommonInput.modifiers(event.modifiers()))) return true;
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!mojangView && ui.charTyped((char) event.codepoint())) return true;
        return super.charTyped(event);
    }

    @Override
    public void onClose() {
        ui.close();
    }
}
