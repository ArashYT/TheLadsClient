/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.thecsdev.common.util.annotations.Reflected
 *  net.minecraft.ChatFormatting
 *  net.minecraft.network.chat.ClickEvent
 *  net.minecraft.network.chat.ClickEvent$OpenUrl
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.HoverEvent
 *  net.minecraft.network.chat.HoverEvent$ShowText
 *  net.minecraft.network.chat.MutableComponent
 *  org.jetbrains.annotations.NotNull
 */
package com.thelads.core.features.alwayson.betterstatisticscreen.resource;

import com.thelads.core.features.alwayson.betterstatisticscreen.BetterStats;
import com.thecsdev.common.util.annotations.Reflected;
import java.net.URI;
import java.util.Objects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.NotNull;

public final class BLanguage {
    public static final Component WATERMARK;

    private BLanguage() {
    }

    public static final MutableComponent mmName_betterstats() {
        return Component.translatable((String)"modmenu.nameTranslation.betterstats");
    }

    public static final MutableComponent mmSummary_betterstats() {
        return Component.translatable((String)"modmenu.summaryTranslation.betterstats");
    }

    public static final MutableComponent stat_betterstats_timeSinceLogin() {
        return Component.translatable((String)"stat.betterstats.time_since_login");
    }

    public static final MutableComponent config_common_registerCommands() {
        return Component.translatable((String)"betterstats.config.common.register_commands");
    }

    public static final MutableComponent config_common_registerCommands_tooltip() {
        return Component.translatable((String)"betterstats.config.common.register_commands.tooltip");
    }

    public static final MutableComponent config_common_apiEndpoint() {
        return Component.translatable((String)"betterstats.config.common.api_endpoint");
    }

    public static final MutableComponent config_common_apiEndpoint_tooltip() {
        return Component.translatable((String)"betterstats.config.common.api_endpoint.tooltip");
    }

    public static final MutableComponent config_client_allowChatPsa() {
        return Component.translatable((String)"betterstats.config.client.allow_chat_psa");
    }

    public static final MutableComponent config_client_allowChatPsa_tooltip() {
        return Component.translatable((String)"betterstats.config.client.allow_chat_psa.tooltip");
    }

    public static final MutableComponent config_client_guiMobsFollowCursor() {
        return Component.translatable((String)"betterstats.config.client.gui_mobs_follow_cursor");
    }

    public static final MutableComponent config_client_guiMobsFollowCursor_tooltip() {
        return Component.translatable((String)"betterstats.config.client.gui_mobs_follow_cursor.tooltip");
    }

    public static final MutableComponent cmd_stats_edit_out(@NotNull Component stat, int affectedPlayerCount) {
        return Component.translatable((String)"commands.statistics.edit.output", (Object[])new Object[]{stat, affectedPlayerCount});
    }

    public static final MutableComponent cmd_stats_clear_out(int affectedPlayerCount) {
        return Component.translatable((String)"commands.statistics.clear.output", (Object[])new Object[]{affectedPlayerCount});
    }

    public static final MutableComponent cmd_stats_clear_kick() {
        return Component.translatable((String)"commands.statistics.clear.kick");
    }

    public static final MutableComponent cmd_stats_query_out(@NotNull Component player, @NotNull Component stat, int value) {
        return Component.translatable((String)"commands.statistics.query.output", (Object[])new Object[]{player, stat, value});
    }

    public static final MutableComponent gui_menubar_file() {
        return Component.translatable((String)"betterstats.gui.menubar.file");
    }

    public static final MutableComponent gui_menubar_file_new() {
        return Component.translatable((String)"betterstats.gui.menubar.file.new");
    }

    public static final MutableComponent gui_menubar_file_open() {
        return Component.translatable((String)"betterstats.gui.menubar.file.open");
    }

    public static final MutableComponent gui_menubar_file_saveAs() {
        return Component.translatable((String)"betterstats.gui.menubar.file.save_as");
    }

    public static final MutableComponent gui_menubar_file_settings() {
        return Component.translatable((String)"betterstats.gui.menubar.file.settings");
    }

    public static final MutableComponent gui_menubar_file_close() {
        return Component.translatable((String)"betterstats.gui.menubar.file.close");
    }

    public static final MutableComponent gui_menubar_view() {
        return Component.translatable((String)"betterstats.gui.menubar.view");
    }

    public static final MutableComponent gui_menubar_view_vanillaScreen() {
        return Component.translatable((String)"betterstats.gui.menubar.view.vanilla_screen");
    }

    public static final MutableComponent gui_menubar_view_homepage() {
        return Component.translatable((String)"betterstats.gui.menubar.view.homepage");
    }

    public static final MutableComponent gui_menubar_view_localPlayerStats() {
        return Component.translatable((String)"betterstats.gui.menubar.view.local_player_stats");
    }

    public static final MutableComponent gui_menubar_view_statsView() {
        return Component.translatable((String)"betterstats.gui.menubar.view.stats_view");
    }

    public static final MutableComponent gui_menubar_about() {
        return Component.translatable((String)"betterstats.gui.menubar.about");
    }

    public static final MutableComponent gui_menubar_about_sourceCode() {
        return Component.translatable((String)"betterstats.gui.menubar.about.source_code");
    }

    public static final MutableComponent gui_menubar_about_supportMe() {
        return Component.translatable((String)"betterstats.gui.menubar.about.support_me");
    }

    public static final MutableComponent gui_menubar_about_legalNotices() {
        return Component.translatable((String)"betterstats.gui.menubar.about.legal_notices");
    }

    public static final MutableComponent gui_statsview_filters() {
        return Component.translatable((String)"betterstats.gui.statsview.filters");
    }

    public static final MutableComponent gui_statsview_filter_selectedView() {
        return Component.translatable((String)"betterstats.gui.statsview.filter.selected_view");
    }

    public static final MutableComponent gui_statsview_filter_search() {
        return Component.translatable((String)"betterstats.gui.statsview.filter.search");
    }

    public static final MutableComponent gui_statsview_filter_showAllStats() {
        return Component.translatable((String)"betterstats.gui.statsview.filter.show_all_stats");
    }

    public static final MutableComponent gui_statsview_filter_sortBy() {
        return Component.translatable((String)"betterstats.gui.statsview.filter.sort_by");
    }

    public static final MutableComponent gui_statsview_filter_groupBy() {
        return Component.translatable((String)"betterstats.gui.statsview.filter.group_by");
    }

    public static final MutableComponent gui_statsview_filter_groupBy_all() {
        return Component.translatable((String)"betterstats.gui.statsview.filter.group_by.all");
    }

    public static final MutableComponent gui_statsview_filter_groupBy_mod() {
        return Component.translatable((String)"betterstats.gui.statsview.filter.group_by.mod");
    }

    public static final MutableComponent gui_statsview_filter_groupBy_mobCategory() {
        return Component.translatable((String)"betterstats.gui.statsview.filter.group_by.mob_category");
    }

    public static final MutableComponent gui_statsview_filter_groupBy_createiveModeTab() {
        return Component.translatable((String)"betterstats.gui.statsview.filter.group_by.creative_mode_tab");
    }

    public static final MutableComponent gui_statsview_filter_distanceUnit() {
        return Component.translatable((String)"betterstats.gui.statsview.filter.distance_unit");
    }

    public static final MutableComponent gui_statsview_filter_timeUnit() {
        return Component.translatable((String)"betterstats.gui.statsview.filter.time_unit");
    }

    public static final MutableComponent gui_statsview_stats_noStats() {
        return Component.translatable((String)"betterstats.gui.statsview.stats.no_stats");
    }

    public static final MutableComponent gui_statsview_stats_ctxMenu_viewErrorInfo() {
        return Component.translatable((String)"betterstats.gui.statsview.stats.ctxmenu.view_error_info");
    }

    public static final MutableComponent gui_statsview_stats_ctxMenu_viewOnWiki() {
        return Component.translatable((String)"betterstats.gui.statsview.stats.ctxmenu.view_on_wiki");
    }

    public static final MutableComponent gui_homeTab_featuredStats() {
        return Component.translatable((String)"betterstats.gui.home_tab.featured_stats");
    }

    @Reflected
    public static final MutableComponent credits() {
        return Component.translatable((String)"betterstats.credits");
    }

    @Reflected
    public static final MutableComponent credits_section_topSponsors() {
        return Component.translatable((String)"betterstats.credits.section.top_sponsors");
    }

    @Reflected
    public static final MutableComponent credits_section_topSponsors_summary() {
        return Component.translatable((String)"betterstats.credits.section.top_sponsors.summary");
    }

    @Reflected
    public static final MutableComponent credits_section_recentSponsors() {
        return Component.translatable((String)"betterstats.credits.section.recent_sponsors");
    }

    @Reflected
    public static final MutableComponent credits_section_recentSponsors_summary() {
        return Component.translatable((String)"betterstats.credits.section.recent_sponsors.summary");
    }

    @Reflected
    public static final MutableComponent credits_section_recentSponsors_entry_sponsor() {
        return Component.translatable((String)"betterstats.credits.section.recent_sponsors.entry.sponsor");
    }

    @Reflected
    public static final MutableComponent credits_section_recentSponsors_entry_sponsor_summary() {
        return Component.translatable((String)"betterstats.credits.section.recent_sponsors.entry.sponsor.summary");
    }

    @Reflected
    public static final MutableComponent credits_section_specialThanks() {
        return Component.translatable((String)"betterstats.credits.section.special_thanks");
    }

    @Reflected
    public static final MutableComponent credits_section_specialThanks_summary() {
        return Component.translatable((String)"betterstats.credits.section.special_thanks.summary");
    }

    @Reflected
    public static final MutableComponent credits_section_specialThanks_entry_you() {
        return Component.translatable((String)"betterstats.credits.section.special_thanks.entry.you");
    }

    @Reflected
    public static final MutableComponent credits_section_specialThanks_entry_you_summary() {
        return Component.translatable((String)"betterstats.credits.section.special_thanks.entry.you.summary");
    }

    @Reflected
    public static final MutableComponent credits_section_specialThanks_entry_contributors() {
        return Component.translatable((String)"betterstats.credits.section.special_thanks.entry.contributors");
    }

    @Reflected
    public static final MutableComponent credits_section_specialThanks_entry_contributors_summary() {
        return Component.translatable((String)"betterstats.credits.section.special_thanks.entry.contributors.summary");
    }

    @Reflected
    public static final MutableComponent credits_section_contributors() {
        return Component.translatable((String)"betterstats.credits.section.contributors");
    }

    @Reflected
    public static final MutableComponent credits_section_contributors_summary() {
        return Component.translatable((String)"betterstats.credits.section.contributors.summary");
    }

    @Reflected
    public static final MutableComponent credits_section_contributors_entry_contribute() {
        return Component.translatable((String)"betterstats.credits.section.contributors.entry.contribute");
    }

    @Reflected
    public static final MutableComponent credits_section_contributors_entry_contribute_summary() {
        return Component.translatable((String)"betterstats.credits.section.contributors.entry.contribute.summary");
    }

    @Reflected
    public static final MutableComponent credits_section_founderContributors() {
        return Component.translatable((String)"betterstats.credits.section.founder_contributors");
    }

    @Reflected
    public static final MutableComponent credits_section_founderContributors_summary() {
        return Component.translatable((String)"betterstats.credits.section.founder_contributors.summary");
    }

    static {
        BetterStats bss = Objects.requireNonNull(BetterStats.getInstance(), "Mod not initialized: 'betterstats'");
        MutableComponent hoverText = Component.literal((String)bss.getModName()).withStyle(ChatFormatting.YELLOW).append("\n").append((Component)Component.literal((String)"betterstats").withStyle(ChatFormatting.GRAY));
        HoverEvent.ShowText hoverEvent = new HoverEvent.ShowText((Component)hoverText);
        ClickEvent.OpenUrl clickEvent = new ClickEvent.OpenUrl(URI.create(BetterStats.getProperty("mod.link.homepage")));
        MutableComponent text = Component.literal((String)"[\u2261] <betterstats>").withStyle(ChatFormatting.DARK_PURPLE);
        text.setStyle(text.getStyle().withHoverEvent((HoverEvent)hoverEvent).withClickEvent((ClickEvent)clickEvent));
        WATERMARK = text;
    }
}

