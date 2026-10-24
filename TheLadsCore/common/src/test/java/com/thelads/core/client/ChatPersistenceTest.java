package com.thelads.core.client;

import com.thelads.core.config.ModuleManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class ChatPersistenceTest {
    @BeforeEach
    public void setup() {
        ChatPersistence.clear();
    }

    @Test
    public void testHistoryStorage() {
        // Module Chat is enabled by default in ModuleManager
        ChatPersistence.recordMessage("mc.hypixel.net", "Message 1");
        ChatPersistence.recordMessage("mc.hypixel.net", "Message 2");

        List<String> hist = ChatPersistence.getHistory("mc.hypixel.net");
        assertNotNull(hist);
    }
}
