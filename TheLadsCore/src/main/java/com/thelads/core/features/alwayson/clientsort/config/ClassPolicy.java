/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.datafixers.util.Pair
 *  joptsimple.internal.Strings
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package com.thelads.core.features.alwayson.clientsort.config;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import com.mojang.datafixers.util.Pair;
import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.config.Config;
import com.thelads.core.features.alwayson.clientsort.config.Operation;
import com.thelads.core.features.alwayson.clientsort.config.Policy;
import com.thelads.core.features.alwayson.clientsort.config.Vec2i;
import com.thelads.core.features.alwayson.clientsort.util.Localization;
import java.text.ParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import joptsimple.internal.Strings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record ClassPolicy(@NotNull String className, @Nullable String invTitle, @Nullable Vec2i buttonOffset, boolean offsetFromSlot, @NotNull Policy sortPolicy, @NotNull Policy stackFillPolicy, @NotNull Policy matchTransferPolicy, @NotNull Policy transferPolicy, @Nullable Operation autoOp, boolean autoOpOther, @NotNull TreeSet<Integer> ignoredSlots) {
    public static final String DATA_FORMAT = "%s/%s,(%s)/%d,%s,%s,%s,%s,%d/%d,(%s)";
    public static final String DATA_PATTERN_STRING = "^([^/]+)/(.+?)?,\\((?:(-?\\d+),(-?\\d+))?\\)/([01]),([012]),([012]),([012]),([012]),([01234])/([01]),\\(((?:\\d+(?:,\\d+)*)?)\\)$";
    public static final Pattern DATA_PATTERN = Pattern.compile("^([^/]+)/(.+?)?,\\((?:(-?\\d+),(-?\\d+))?\\)/([01]),([012]),([012]),([012]),([012]),([01234])/([01]),\\(((?:\\d+(?:,\\d+)*)?)\\)$");

    @NotNull
    public String getKey() {
        return ClassPolicy.getKey(this.className, this.invTitle);
    }

    @NotNull
    public Vec2i getButtonOffset() {
        return this.buttonOffset == null ? Config.options().layoutOffset : this.buttonOffset;
    }

    public boolean canSort() {
        return !this.sortPolicy.equals((Object)Policy.NONE);
    }

    public boolean canStackFill() {
        return !this.stackFillPolicy.equals((Object)Policy.NONE);
    }

    public boolean canMatchTransfer() {
        return !this.matchTransferPolicy.equals((Object)Policy.NONE);
    }

    public boolean canTransfer() {
        return !this.transferPolicy.equals((Object)Policy.NONE);
    }

    public boolean useSortKeybind() {
        return this.sortPolicy.keybind;
    }

    public boolean useStackFillKeybind() {
        return this.stackFillPolicy.keybind;
    }

    public boolean useMatchTransferKeybind() {
        return this.matchTransferPolicy.keybind;
    }

    public boolean useTransferKeybind() {
        return this.transferPolicy.keybind;
    }

    public boolean showSortButton() {
        return this.sortPolicy.button;
    }

    public boolean showStackFillButton() {
        return this.stackFillPolicy.button;
    }

    public boolean showMatchTransferButton() {
        return this.matchTransferPolicy.button;
    }

    public boolean showTransferButton() {
        return this.transferPolicy.button;
    }

    public boolean autoSort() {
        return this.autoOp == Operation.SORT;
    }

    public boolean autoStackFill() {
        return this.autoOp == Operation.STACK_FILL;
    }

    public boolean autoMatchTransfer() {
        return this.autoOp == Operation.MATCH_TRANSFER;
    }

    public boolean autoTransfer() {
        return this.autoOp == Operation.TRANSFER;
    }

    public String toDataString() {
        return String.format(DATA_FORMAT, this.className, this.invTitle == null ? "" : this.invTitle, this.buttonOffset == null ? "" : this.buttonOffset.x() + "," + this.buttonOffset.y(), this.offsetFromSlot ? 1 : 0, this.sortPolicy.toSimpleString(), this.stackFillPolicy.toSimpleString(), this.matchTransferPolicy.toSimpleString(), this.transferPolicy.toSimpleString(), this.autoOp == null ? 0 : List.of(Operation.values()).indexOf((Object)this.autoOp) + 1, this.autoOpOther ? 1 : 0, Strings.join(this.ignoredSlots.stream().map(String::valueOf).toList(), (String)","));
    }

    public static ClassPolicy fromDataString(String dataString, Set<String> oldPolicyKeys) throws ParseException {
        String invTitle;
        Matcher matcher = DATA_PATTERN.matcher(dataString = dataString.strip());
        if (!matcher.matches()) {
            throw new ParseException(Localization.localized("error", "classPolicy.pattern", DATA_PATTERN_STRING).getString(), 0);
        }
        String className = matcher.group(1);
        if (!oldPolicyKeys.contains(ClassPolicy.getKey(className, invTitle = matcher.group(2)))) {
            try {
                Class.forName(className);
            }
            catch (ClassNotFoundException e) {
                throw new ParseException(Localization.localized("error", "classPolicy.classNotFound", className).getString(), 0);
            }
        }
        return new ClassPolicy(className, invTitle, matcher.group(3) == null ? null : new Vec2i(Integer.parseInt(matcher.group(3)), Integer.parseInt(matcher.group(4))), matcher.group(5).equals("1"), Policy.fromSimpleString(matcher.group(6)), Policy.fromSimpleString(matcher.group(7)), Policy.fromSimpleString(matcher.group(8)), Policy.fromSimpleString(matcher.group(9)), matcher.group(10).equals("0") ? null : Operation.values()[Integer.parseInt(matcher.group(10)) - 1], matcher.group(11).equals("1"), new TreeSet<Integer>(Arrays.stream(matcher.group(12).split(",")).filter(s -> !s.isBlank()).map(Integer::parseInt).sorted().toList()));
    }

    public static boolean hasInvTitle(@NotNull String key) {
        return key.contains("/");
    }

    public static ClassPolicy create(@NotNull String key, @Nullable Vec2i buttonOffset, boolean offsetFromSlot, @NotNull Policy sortPolicy, @NotNull Policy stackFillPolicy, @NotNull Policy matchTransferPolicy, @NotNull Policy transferPolicy, @Nullable Operation autoOp, boolean autoOpOther, @NotNull TreeSet<Integer> ignoredSlots) {
        Pair<String, String> splitKey = ClassPolicy.parseKey(key);
        return new ClassPolicy((String)splitKey.getFirst(), (String)splitKey.getSecond(), buttonOffset, offsetFromSlot, sortPolicy, stackFillPolicy, matchTransferPolicy, transferPolicy, autoOp, autoOpOther, ignoredSlots);
    }

    @NotNull
    public static Pair<String, String> parseKey(@NotNull String keyStr) {
        String[] split = keyStr.split("/", 2);
        return new Pair((Object)split[0], split.length > 1 ? split[1] : null);
    }

    @NotNull
    public static String getKey(@NotNull String className, @Nullable String invTitle) {
        if (className.contains("/")) {
            ClientSort.LOG.error("Cannot get ClassPolicy key for input strings '{}', '{}'", className, invTitle);
            return className;
        }
        return className + (String)(invTitle == null ? "" : "/" + invTitle);
    }
}
