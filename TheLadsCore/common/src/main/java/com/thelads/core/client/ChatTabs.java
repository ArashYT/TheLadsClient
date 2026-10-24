package com.thelads.core.client;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Chat tabs (ALL, PARTY, GUILD, PMS, and custom regex filter tabs).
 * Filters displayed chat messages according to the active tab.
 */
public final class ChatTabs {
    public static final class Tab {
        private final String name;
        private final Pattern pattern;

        public Tab(String name, String regex) {
            this.name = name;
            this.pattern = (regex == null || regex.isEmpty()) ? null : Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        }

        public String getName() {
            return name;
        }

        public boolean matches(String text) {
            if (pattern == null) return true; // Matches everything (e.g. ALL)
            if (text == null) return false;
            return pattern.matcher(text).find();
        }
    }

    private static final List<Tab> TABS = new ArrayList<>();
    private static int activeTab = 0;

    static {
        resetDefaultTabs();
    }

    public static synchronized void resetDefaultTabs() {
        TABS.clear();
        TABS.add(new Tab("ALL", null));
        TABS.add(new Tab("PARTY", "(?:\\[Party\\]|Party >|^P >)"));
        TABS.add(new Tab("GUILD", "(?:\\[Guild\\]|Guild >|^G >)"));
        TABS.add(new Tab("PMS", "(?:^From |^To |\\[PM\\]| whispers:)"));
        activeTab = 0;
    }

    private ChatTabs() {}

    public static boolean enabled() {
        Module chat = ModuleManager.getInstance().getModule("Chat");
        return chat != null && chat.isEnabled() && chat.getOption("Chat Tabs") instanceof BoolOption opt && opt.get();
    }

    public static synchronized int getActiveTab() {
        return activeTab;
    }

    public static synchronized void setActiveTab(int index) {
        if (index >= 0 && index < TABS.size()) {
            activeTab = index;
        }
    }

    public static synchronized int getTabCount() {
        return TABS.size();
    }

    public static synchronized Tab getTab(int index) {
        if (index >= 0 && index < TABS.size()) {
            return TABS.get(index);
        }
        return TABS.get(0);
    }

    public static synchronized void addTab(String name, String regex) {
        TABS.add(new Tab(name, regex));
    }

    public static synchronized boolean matches(int tabIndex, String unformattedText) {
        if (tabIndex < 0 || tabIndex >= TABS.size()) return true;
        return TABS.get(tabIndex).matches(unformattedText);
    }

    public static synchronized boolean filterCurrentTab(String unformattedText) {
        if (!enabled()) return true;
        return matches(activeTab, unformattedText);
    }
}
