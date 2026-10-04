package com.thelads.core.client;

import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * AutoReconnect's state on every version: the server or world last joined, the disconnect screen counting down, and the actions
 * queued after an automatic reconnect. Adapters feed it game events; every call is on the client thread.
 */
public final class ReconnectSession {
    private final ReconnectPlan plan = new ReconnectPlan();
    private final ReconnectActions actions = new ReconnectActions();
    private String target;
    private Runnable connector;
    private UUID account;
    private boolean joined, reconnecting, reasonAllowed;
    private Object dialog;
    private String reason = "";

    /**
     * A server connection or world load started. Ignored while this session itself is reconnecting, so the attempt count
     * carries over to the next disconnect screen.
     */
    public void begin(String id, Runnable connect, UUID player) {
        if (reconnecting) return;
        clear();
        target = id;
        connector = connect;
        account = player;
    }

    public String target() { return target == null ? "" : target; }
    public String reason() { return reason; }
    public boolean reconnecting() { return reconnecting; }

    /** A retry makes sense: there is a target, the same account is signed in, and it was joined once (or initial failures retry). */
    public boolean canRetry(UUID player, boolean retryInitialFailures) {
        return target != null && account.equals(player) && (joined || retryInitialFailures);
    }

    /** A disconnect screen opened (or re-initialized): a new one evaluates its reason and starts the countdown when allowed. */
    public void disconnected(Object screen, List<String> reasonKeys, String reasonText, ReconnectSettings lists, boolean onlyMatches,
                             boolean repeatLastDelay, long now) {
        if (screen == dialog) return;
        dialog = screen;
        reason = reasonText;
        reasonAllowed = ReconnectFilters.allows(reasonKeys, reasonText, lists.reasonKeys, lists.reasonPatterns, onlyMatches);
        if (reasonAllowed) plan.schedule(lists.retryDelays, repeatLastDelay, now);
    }

    /** Each client tick with `screen` showing: true once its countdown ran out (the caller then calls reconnect()). */
    public boolean due(Object screen, long now) { return screen != null && screen == dialog && plan.takeDue(now); }

    /** Connect to the target again, now. */
    public void reconnect() {
        if (connector == null) return;
        reconnecting = true;
        try { connector.run(); } finally { reconnecting = false; }
    }

    /** The Reconnect button: a manual retry, which never sends the join actions. */
    public void reconnectNow() {
        plan.cancel();
        actions.clear();
        reconnect();
    }

    /** Cancel button / Escape. True when a countdown was running. */
    public boolean cancelCountdown() {
        boolean counting = plan.pending();
        plan.cancel();
        actions.clear();
        reasonAllowed = false;
        return counting;
    }

    /** Forget the target (back in the menus, account switched or module off). */
    public void clear() {
        cancelCountdown();
        target = null;
        connector = null;
        account = null;
        joined = false;
        dialog = null;
        reason = "";
    }

    public boolean counting() { return plan.pending(); }

    /** The retry button's label: the countdown, or why it stopped. */
    public String retryLabel(long now) {
        int seconds = plan.secondsLeft(now);
        if (seconds >= 0) return "Reconnect in " + seconds + "s";
        return reasonAllowed ? "Retry limit reached · Reconnect" : "Reconnect";
    }

    /**
     * Joined the target. After an automatic reconnect, queues the enabled join actions for this target on `connection`.
     * Returns true when this join was an automatic reconnect.
     */
    public boolean joined(Object connection, ReconnectSettings lists, boolean actionsOn, boolean regexTargets, boolean signed, long now) {
        joined = true;
        dialog = null;
        actions.clear();
        boolean automatic = plan.joined();
        if (!automatic || !actionsOn) return automatic;
        actions.begin(now);
        for (ReconnectSettings.JoinAction action : lists.joinActions)
            if (action.enabled && ReconnectFilters.contextMatches(action.target, target, regexTargets))
                actions.add(connection, account, action.interval, action.lines, signed);
        return true;
    }

    /** Each client tick: sends the join actions that are due, only on the connection and account they were queued for. */
    public void sendDue(Object connection, UUID player, boolean actionsOn, long now, BiConsumer<String, Boolean> send) {
        actions.drain(connection, player, actionsOn, now, send);
    }
}
