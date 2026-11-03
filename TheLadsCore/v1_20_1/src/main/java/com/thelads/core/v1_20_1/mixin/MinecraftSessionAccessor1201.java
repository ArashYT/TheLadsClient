package com.thelads.core.v1_20_1.mixin;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.realmsclient.gui.RealmsDataFetcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.telemetry.ClientTelemetryManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** All of Minecraft that is built from the logged-in account, for an account switch without a restart (LiveSession1201). */
@Mixin(Minecraft.class)
public interface MinecraftSessionAccessor1201 {
    @Accessor("authenticationService") YggdrasilAuthenticationService lads$authService();
    @Accessor("profileProperties") PropertyMap lads$profileProperties();
    @Mutable @Accessor("user") void lads$setUser(User user);
    @Mutable @Accessor("userApiService") void lads$setUserApiService(UserApiService service);
    @Mutable @Accessor("playerSocialManager") void lads$setPlayerSocialManager(PlayerSocialManager manager);
    @Mutable @Accessor("telemetryManager") void lads$setTelemetryManager(ClientTelemetryManager manager);
    @Mutable @Accessor("profileKeyPairManager") void lads$setProfileKeyPairManager(ProfileKeyPairManager manager);
    @Mutable @Accessor("realmsDataFetcher") void lads$setRealmsDataFetcher(RealmsDataFetcher fetcher);
    @Accessor("reportingContext") void lads$setReportingContext(ReportingContext context);
}
