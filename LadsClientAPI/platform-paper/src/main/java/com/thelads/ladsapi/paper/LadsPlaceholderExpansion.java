package com.thelads.ladsapi.paper;

import com.thelads.ladsapi.LadsCore;
import com.thelads.ladsapi.api.LadsClientAPI;
import com.thelads.ladsapi.api.LadsPlayer;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * PlaceholderAPI expansion for %ladsapi_badge%, %ladsapi_is_lads%, %ladsapi_version%.
 */
public final class LadsPlaceholderExpansion extends PlaceholderExpansion {
    private final LadsCore core;

    public LadsPlaceholderExpansion(LadsCore core) {
        this.core = core;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "ladsapi";
    }

    @Override
    public @NotNull String getAuthor() {
        return "The Lads";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";

        Optional<LadsPlayer> optLp = LadsClientAPI.get() != null
                ? LadsClientAPI.get().player(player.getUniqueId())
                : Optional.empty();

        boolean isLads = optLp.isPresent();

        if ("badge".equalsIgnoreCase(params)) {
            return isLads ? core.config().getBadgePlaceholder() : "";
        }
        if ("is_lads".equalsIgnoreCase(params)) {
            return isLads ? "true" : "false";
        }
        if ("version".equalsIgnoreCase(params)) {
            return isLads ? optLp.get().clientVersion() : "";
        }

        return null;
    }
}
