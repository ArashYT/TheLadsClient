package com.thelads.core.v26_2.gui;

import com.mojang.authlib.exceptions.AuthenticationUnavailableException;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.FriendsService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.realmsclient.client.RealmsClient;
import com.mojang.realmsclient.gui.RealmsDataFetcher;
import com.thelads.core.client.auth.LadsAccount;
import com.thelads.core.client.auth.LiveSession;
import com.thelads.core.v26_2.mixin.MinecraftSessionAccessor26;
import com.thelads.core.v26_2.mixin.RealmsClientAccessor26;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.gui.screens.social.RemoteFriendListUpdateHandler;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportEnvironment;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.telemetry.ClientTelemetryManager;
import net.minecraft.server.Services;
import net.minecraft.util.Util;

/** Minecraft.user and everything built from it at startup, swapped in the running game. */
final class LiveSession26 implements LiveSession {
    private final Minecraft mc = Minecraft.getInstance();

    @Override
    public String username() {
        return mc.getUser().getName();
    }

    @Override
    public String uuid() {
        return mc.getUser().getProfileId().toString();
    }

    @Override
    public boolean inWorld() {
        return mc.level != null;
    }

    @Override
    public Runnable prepare(LadsAccount target, String accessToken) throws Exception {
        boolean offline = target.isOffline();
        User user = new User(target.getUsername(), target.sessionUuid(), accessToken, Optional.empty(), Optional.empty());
        YggdrasilAuthenticationService auth = offline
            ? YggdrasilAuthenticationService.createOffline(mc.getProxy())
            : new YggdrasilAuthenticationService(mc.getProxy());
        UserApiService api = offline ? UserApiService.OFFLINE : auth.createUserApiService(accessToken);
        // Asks Mojang: a token it refuses throws here, before anything changed; unreachable Mojang degrades cleanly to offline properties.
        UserApiService.UserProperties properties;
        if (offline) {
            properties = UserApiService.OFFLINE_PROPERTIES;
        } else {
            try {
                properties = api.fetchProperties();
            } catch (AuthenticationUnavailableException unavailable) {
                properties = UserApiService.OFFLINE_PROPERTIES;
            }
        }
        FriendsService friends = auth.createFriendsService(accessToken);
        Services services = Services.create(auth, mc.gameDirectory);
        CompletableFuture<ProfileResult> profile = offline
            ? CompletableFuture.completedFuture(null)
            : CompletableFuture.supplyAsync(() -> {
                try {
                    return services.sessionService().fetchProfile(user.getProfileId(), true);
                } catch (RuntimeException e) {
                    return null;
                }
            }, Util.nonCriticalIoPool());
        UserApiService.UserProperties resolvedProperties = properties;
        return () -> {
            if (mc.level != null) {
                mc.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE); // saves a singleplayer world before it returns
                mc.setScreenAndShow(new TitleScreen());
            }
            MinecraftSessionAccessor26 game = (MinecraftSessionAccessor26) mc;
            User prevUser = mc.getUser();
            Services prevServices = game.lads$services();
            CompletableFuture<ProfileResult> prevProfile = game.lads$profileFuture();
            UserApiService prevApi = game.lads$userApiService();
            CompletableFuture<UserApiService.UserProperties> prevProps = game.lads$userPropertiesFuture();
            RemoteFriendListUpdateHandler prevHandler = game.lads$friendListHandler();
            PlayerSocialManager prevSocial = mc.getPlayerSocialManager();
            ProfileKeyPairManager prevKeys = mc.getProfileKeyPairManager();
            ReportingContext prevReporting = mc.getReportingContext();
            try {
                RemoteFriendListUpdateHandler handler = new RemoteFriendListUpdateHandler(friends, mc);
                PlayerSocialManager social = new PlayerSocialManager(mc, api, friends, handler);
                ProfileKeyPairManager nextKeys = offline
                    ? ProfileKeyPairManager.EMPTY_KEY_MANAGER
                    : ProfileKeyPairManager.create(api, user, mc.gameDirectory.toPath());
                ReportingContext nextReporting = ReportingContext.create(ReportEnvironment.local(), api);
                game.lads$setServices(services);
                game.lads$setUser(user);
                game.lads$setProfileFuture(profile);
                game.lads$setUserApiService(api);
                game.lads$setUserPropertiesFuture(CompletableFuture.completedFuture(resolvedProperties));
                if (mc.gui != null && mc.gui.splashManager() != null) {
                    ((MinecraftSessionAccessor26.Splash) mc.gui.splashManager()).lads$setUser(user);
                }
                if (prevHandler != null) prevHandler.close();
                game.lads$setFriendListHandler(handler);
                game.lads$setPlayerSocialManager(social);
                if (!offline && social.isFriendListEnabled()) handler.start();
                mc.getTelemetryManager().close();
                game.lads$setTelemetryManager(new ClientTelemetryManager(mc, api, user));
                game.lads$setProfileKeyPairManager(nextKeys);
                game.lads$setReportingContext(nextReporting);
                RealmsClientAccessor26.lads$setInstance(null);
                game.lads$setRealmsDataFetcher(new RealmsDataFetcher(RealmsClient.getOrCreate(mc)));
            } catch (RuntimeException | Error e) {
                game.lads$setServices(prevServices);
                game.lads$setUser(prevUser);
                game.lads$setProfileFuture(prevProfile);
                game.lads$setUserApiService(prevApi);
                game.lads$setUserPropertiesFuture(prevProps);
                if (mc.gui != null && mc.gui.splashManager() != null) {
                    ((MinecraftSessionAccessor26.Splash) mc.gui.splashManager()).lads$setUser(prevUser);
                }
                game.lads$setFriendListHandler(prevHandler);
                game.lads$setPlayerSocialManager(prevSocial);
                game.lads$setProfileKeyPairManager(prevKeys);
                game.lads$setReportingContext(prevReporting);
                RealmsClientAccessor26.lads$setInstance(null);
                throw e;
            }
        };
    }
}
