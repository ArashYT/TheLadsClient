package com.thelads.core;

import com.thelads.core.client.auth.AccountSwitcherScreen;
import com.thelads.core.client.auth.LadsAccount;
import com.thelads.core.client.util.ClientPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class AccountSwitcherTest {

    @Test
    public void testAccountSwitchingAndPersistence(@TempDir Path tempDir) {
        ClientPaths.setBaseDir(tempDir);

        AccountSwitcherScreen screen = new AccountSwitcherScreen();
        assertNotNull(screen.getSelectedAccount(), "Default account should be selected");

        screen.addOfflineAccount("ArashTest");
        LadsAccount selected = screen.getSelectedAccount();
        assertNotNull(selected);
        assertEquals("ArashTest", selected.getUsername());
        assertTrue(selected.isSelected());

        assertTrue(ClientPaths.getAccountsFile().exists(), "Accounts file should be saved");
        assertTrue(ClientPaths.getProfileFile().exists(), "Profile file should be saved");
    }
}
