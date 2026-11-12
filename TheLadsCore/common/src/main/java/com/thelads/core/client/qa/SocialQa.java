package com.thelads.core.client.qa;

import com.thelads.core.client.WorldListOrder;
import com.thelads.core.client.social.BestFriends;
import com.thelads.core.client.social.SocialState;
import com.thelads.core.client.title.TitleOverlay;
import java.util.Arrays;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA script steps for the 1.13.15 social features, shared by every version's probe:
 * {@code sidebar friends|messages}, {@code best <uuid>}, {@code pin <world folder>}, {@code party invite|promote|kick|leave|warp <uuid or address>}.
 * The probe's {@code textui} verb hands the words after it to {@link #step}.
 */
public final class SocialQa {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");

    private SocialQa() {}

    /** True when the verb was one of ours (and has run). */
    public static boolean step(String[] s, int verb) {
        String v = s[verb];
        switch (v) {
            case "sidebar" -> TitleOverlay.qaSidebar("friends".equals(word(s, verb + 1)));
            case "best" -> BestFriends.toggle(uuid(word(s, verb + 1)));
            case "pin" -> WorldListOrder.togglePin(String.join(" ", Arrays.copyOfRange(s, Math.min(verb + 1, s.length), s.length)));
            case "party" -> {
                SocialState st = SocialState.get();
                switch (word(s, verb + 1)) {
                    case "invite" -> st.inviteToParty(uuid(word(s, verb + 2)));
                    case "promote" -> st.promoteInParty(uuid(word(s, verb + 2)));
                    case "kick" -> st.kickFromParty(uuid(word(s, verb + 2)));
                    case "leave" -> st.leaveParty();
                    case "warp" -> st.warp(word(s, verb + 2));
                    default -> throw new IllegalArgumentException("party: " + word(s, verb + 1));
                }
                LOGGER.info("Lads qa party: {} leader={} invites={} warp={}", st.party(), st.isPartyLeader(), st.partyInvites(), st.pendingPartyWarp());
            }
            default -> { return false; }
        }
        return true;
    }

    private static String word(String[] s, int i) {
        if (i >= s.length) throw new IllegalArgumentException("missing argument after " + String.join(" ", s));
        return s[i];
    }

    private static UUID uuid(String id) {
        return UUID.fromString(id.contains("-") ? id : id.replaceFirst("(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5"));
    }
}
