/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.jetbrains.annotations.Nullable
 */
package com.thelads.core.features.alwayson.clientsort.config;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import com.thelads.core.features.alwayson.clientsort.util.Localization;
import java.text.ParseException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;

public class ServerClassPolicy {
    public static final String DATA_FORMAT = "%s,%d,%d,%d";
    public static final String DATA_PATTERN_STRING = "^(.+),([01]),([01]),([01])$";
    public static final Pattern DATA_PATTERN = Pattern.compile("^(.+),([01]),([01]),([01])$");
    public final String className;
    public boolean sortEnabled;
    public boolean stackFillEnabled;
    public boolean transferEnabled;
    @Nullable
    public String lastAutoEditTime;
    @Nullable
    public String lastAutoEditReason;

    public ServerClassPolicy(String className, boolean sortEnabled, boolean stackFillEnabled, boolean transferEnabled) {
        this(className, sortEnabled, stackFillEnabled, transferEnabled, null, null);
    }

    public ServerClassPolicy(String className, boolean sortEnabled, boolean stackFillEnabled, boolean transferEnabled, @Nullable String lastAutoEditTime, @Nullable String lastEditReason) {
        this.className = className;
        this.sortEnabled = sortEnabled;
        this.stackFillEnabled = stackFillEnabled;
        this.transferEnabled = transferEnabled;
        this.lastAutoEditTime = lastAutoEditTime;
        this.lastAutoEditReason = lastEditReason;
    }

    public void setFrom(ServerClassPolicy classPolicy) {
        this.sortEnabled = this.sortEnabled && classPolicy.sortEnabled;
        this.stackFillEnabled = this.stackFillEnabled && classPolicy.stackFillEnabled;
        this.transferEnabled = this.transferEnabled && classPolicy.transferEnabled;
    }

    public String toDataString() {
        return String.format(DATA_FORMAT, this.className, this.sortEnabled ? 1 : 0, this.stackFillEnabled ? 1 : 0, this.transferEnabled ? 1 : 0);
    }

    public static ServerClassPolicy fromDataString(String dataString, Set<String> originalClassNames) throws ParseException {
        Matcher matcher = DATA_PATTERN.matcher(dataString = dataString.strip());
        if (!matcher.matches()) {
            throw new ParseException(Localization.localized("error", "classPolicy.pattern", DATA_PATTERN_STRING).getString(), 0);
        }
        String className = matcher.group(1);
        if (!originalClassNames.contains(className)) {
            try {
                Class.forName(className);
            }
            catch (ClassNotFoundException e) {
                throw new ParseException(Localization.localized("error", "classPolicy.classNotFound", className).getString(), 0);
            }
        }
        return new ServerClassPolicy(className, matcher.group(2).equals("1"), matcher.group(3).equals("1"), matcher.group(4).equals("1"));
    }
}
