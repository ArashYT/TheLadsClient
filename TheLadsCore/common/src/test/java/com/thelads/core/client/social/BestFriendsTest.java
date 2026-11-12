package com.thelads.core.client.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BestFriendsTest {
    @Test
    void roundTripsAndIgnoresJunk() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        Set<UUID> set = new LinkedHashSet<>(java.util.List.of(a, b));
        assertEquals(set, BestFriends.parse(BestFriends.serialize(set)));
        assertTrue(BestFriends.parse("not json").isEmpty());
        assertTrue(BestFriends.parse("{\"best\":[\"x\"]}").isEmpty());
    }
}
