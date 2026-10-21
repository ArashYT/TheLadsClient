package com.thelads.core.client.hypixel;

import static org.junit.jupiter.api.Assertions.*;

import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.client.bridge.DefaultGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChatEmotesTest {
    private LadsGameBridge oldBridge;

    @BeforeEach
    void setUp() {
        oldBridge = LadsGameBridge.get();
        LadsGameBridge.set(new DefaultGameBridge() {
            @Override public String getServerAddress() { return "mc.hypixel.net"; }
        });
        HypixelSafetyManager.qaOverride = true;
        HypixelMods.setMvpPlusPlusForTest(false);
        Module m = ModuleManager.getInstance().getModule(HypixelMods.HYPIXEL_MODS);
        if (m != null) {
            m.setEnabled(true);
            ((BoolOption) m.getOption(HypixelMods.CHAT_EMOTES)).set(true);
        }
    }

    @AfterEach
    void tearDown() {
        HypixelSafetyManager.qaOverride = false;
        LadsGameBridge.set(oldBridge);
        HypixelMods.setMvpPlusPlusForTest(false);
    }

    @Test
    void allMvpPlusPlusEmotesReplaced() {
        assertEquals("¯\\_(ツ)_/¯", ChatEmotes.replaceEmotes(":shrug:"));
        assertEquals("(╯°□°）╯︵ ┻━┻", ChatEmotes.replaceEmotes(":tableflip:"));
        assertEquals("(╯°□°）╯︵ ┻━┻", ChatEmotes.replaceEmotes(":flip:"));
        assertEquals("✔", ChatEmotes.replaceEmotes(":yes:"));
        assertEquals("✖", ChatEmotes.replaceEmotes(":no:"));
        assertEquals("❤", ChatEmotes.replaceEmotes(":heart:"));
        assertEquals("✮", ChatEmotes.replaceEmotes(":star:"));
        assertEquals("◎_◎", ChatEmotes.replaceEmotes(":totem:"));
        assertEquals("✎...", ChatEmotes.replaceEmotes(":typing:"));
        assertEquals("√(π+x)=L", ChatEmotes.replaceEmotes(":maths:"));
        assertEquals("@'-'", ChatEmotes.replaceEmotes(":snail:"));
        assertEquals("(0.o?)", ChatEmotes.replaceEmotes(":thinking:"));
        assertEquals("༼つ ◕_◕ ༽つ", ChatEmotes.replaceEmotes(":gimme:"));
        assertEquals("('-')⊃━☆ﾟ.*･｡ﾟ", ChatEmotes.replaceEmotes(":wizard:"));
        assertEquals("⚔", ChatEmotes.replaceEmotes(":pvp:"));
        assertEquals("✌", ChatEmotes.replaceEmotes(":peace:"));
        assertEquals("OOF", ChatEmotes.replaceEmotes(":oof:"));
        assertEquals("<('O')>", ChatEmotes.replaceEmotes(":puffer:"));
        assertEquals("☃", ChatEmotes.replaceEmotes(":snow:"));
        assertEquals("(ᵔᴥᵔ)", ChatEmotes.replaceEmotes(":dog:"));
        assertEquals("( ⬩ ⊝ ⬩ )", ChatEmotes.replaceEmotes(":sloth:"));
        assertEquals("<o/", ChatEmotes.replaceEmotes(":dab:"));
        assertEquals("= ＾● ⋏ ●＾ =", ChatEmotes.replaceEmotes(":cat:"));
        assertEquals("ヽ (◕◡◕) ﾉ", ChatEmotes.replaceEmotes(":yey:"));
        assertEquals("☕", ChatEmotes.replaceEmotes(":java:"));
        assertEquals("➜", ChatEmotes.replaceEmotes(":arrow:"));
        assertEquals("123", ChatEmotes.replaceEmotes(":123:"));
        assertEquals("(✿ᴖ‿ᴖ)", ChatEmotes.replaceEmotes(":cute:"));
        assertEquals("ヽ(⌐■_■)ノ♬", ChatEmotes.replaceEmotes(":dj:"));
    }

    @Test
    void caseInsensitiveShortcodes() {
        assertEquals("¯\\_(ツ)_/¯", ChatEmotes.replaceEmotes(":SHRUG:"));
        assertEquals("(╯°□°）╯︵ ┻━┻", ChatEmotes.replaceEmotes(":TableFlip:"));
        assertEquals("✔", ChatEmotes.replaceEmotes(":YES:"));
    }

    @Test
    void wordBoundaryEmotes() {
        assertEquals("Hello ❤ world", ChatEmotes.replaceEmotes("Hello <3 world"));
        assertEquals("❤", ChatEmotes.replaceEmotes("<3"));
        assertEquals("Hey ( ﾟ◡ﾟ)/", ChatEmotes.replaceEmotes("Hey o/"));
    }

    @Test
    void embeddedInSentence() {
        assertEquals("well that happened ¯\\_(ツ)_/¯ gg", ChatEmotes.replaceEmotes("well that happened :shrug: gg"));
    }

    @Test
    void mvpPlusPlusCheck() {
        // As a non-MVP++ player with Chat emotes ON
        HypixelMods.setMvpPlusPlusForTest(false);
        assertEquals("¯\\_(ツ)_/¯", HypixelMods.handleOutgoingChat(":shrug:"));

        // As an MVP++ player, the client leaves the text as-is so Hypixel can handle it natively
        HypixelMods.setMvpPlusPlusForTest(true);
        assertEquals(":shrug:", HypixelMods.handleOutgoingChat(":shrug:"));
    }
}
