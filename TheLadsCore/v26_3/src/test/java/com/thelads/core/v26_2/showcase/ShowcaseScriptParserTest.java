package com.thelads.core.v26_2.showcase;

import java.util.List;

public final class ShowcaseScriptParserTest {
    private static int checks = 0;

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Check failed: " + message);
        }
        checks++;
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null && actual == null) {
            checks++;
            return;
        }
        if (expected != null && expected.equals(actual)) {
            checks++;
            return;
        }
        throw new AssertionError("Mismatch in " + message + ": expected <" + expected + "> but got <" + actual + ">");
    }

    public static void main(String[] args) {
        new ShowcaseScriptParserTest().testCommentsAndBlankLines();
        new ShowcaseScriptParserTest().testBasicVerbs();
        new ShowcaseScriptParserTest().testSpatialAndMotionVerbs();
        new ShowcaseScriptParserTest().testInteractionVerbs();
        new ShowcaseScriptParserTest().testBatchLines();
        new ShowcaseScriptParserTest().testQuotedArgs();
        new ShowcaseScriptParserTest().testServerModeAndCommandRouting();
        new ShowcaseScriptParserTest().testSpotParsing();
        new ShowcaseScriptParserTest().testWindowTitleResolution();
        new ShowcaseScriptParserTest().testResetCommandBuilding();
        System.out.println("ShowcaseScriptParserTest PASSED (" + checks + " checks verified)");
    }

    public void testCommentsAndBlankLines() {
        check(ShowcaseScriptParser.parseLine("", 1) == null, "empty line");
        check(ShowcaseScriptParser.parseLine("   ", 2) == null, "blank line");
        check(ShowcaseScriptParser.parseLine("# comment", 3) == null, "comment line");
        check(ShowcaseScriptParser.parseLine("   # indented", 4) == null, "indented comment");
    }

    public void testBasicVerbs() {
        var waitCmd = ShowcaseScriptParser.parseLine("wait 1500", 1);
        check(waitCmd != null, "wait not null");
        assertEquals("wait", waitCmd.verb(), "wait verb");
        assertEquals(1500L, waitCmd.longArg(0, 0), "wait ms");

        var markCmd = ShowcaseScriptParser.parseLine("mark opening-shot extra label", 2);
        check(markCmd != null, "mark not null");
        assertEquals("mark", markCmd.verb(), "mark verb");
        assertEquals("opening-shot extra label", markCmd.tail(0), "mark tail");

        var cmdCmd = ShowcaseScriptParser.parseLine("cmd time set noon", 3);
        check(cmdCmd != null, "cmd not null");
        assertEquals("cmd", cmdCmd.verb(), "cmd verb");
        assertEquals("time set noon", cmdCmd.tail(0), "cmd tail");

        var chatCmd = ShowcaseScriptParser.parseLine("chat /help", 4);
        check(chatCmd != null, "chat not null");
        assertEquals("chat", chatCmd.verb(), "chat verb");
        assertEquals("/help", chatCmd.tail(0), "chat tail");
    }

    public void testSpatialAndMotionVerbs() {
        var tpCmd = ShowcaseScriptParser.parseLine("tp 1.5 0.0 -2.5 180.0 -10.0", 5);
        check(tpCmd != null, "tp not null");
        assertEquals("tp", tpCmd.verb(), "tp verb");
        assertEquals(1.5, tpCmd.doubleArg(0, 0), "tp dx");
        assertEquals(0.0, tpCmd.doubleArg(1, -99), "tp dy");
        assertEquals(-2.5, tpCmd.doubleArg(2, 0), "tp dz");
        assertEquals(180.0, tpCmd.doubleArg(3, 0), "tp yaw");
        assertEquals(-10.0, tpCmd.doubleArg(4, 0), "tp pitch");

        var spotCmd = ShowcaseScriptParser.parseLine("spot plains", 6);
        check(spotCmd != null, "spot not null");
        assertEquals("spot", spotCmd.verb(), "spot verb");
        assertEquals("plains", spotCmd.arg(0, ""), "spot biome");

        var lookCmd = ShowcaseScriptParser.parseLine("look 90.0 -5.0 800", 7);
        check(lookCmd != null, "look not null");
        assertEquals("look", lookCmd.verb(), "look verb");
        assertEquals(90.0, lookCmd.doubleArg(0, 0), "look yaw");
        assertEquals(-5.0, lookCmd.doubleArg(1, 0), "look pitch");
        assertEquals(800L, lookCmd.longArg(2, 0), "look ms");

        var turnCmd = ShowcaseScriptParser.parseLine("turn 45.0 10.0 400", 8);
        check(turnCmd != null, "turn not null");
        assertEquals("turn", turnCmd.verb(), "turn verb");
        assertEquals(45.0, turnCmd.doubleArg(0, 0), "turn dyaw");
        assertEquals(10.0, turnCmd.doubleArg(1, 0), "turn dpitch");
        assertEquals(400L, turnCmd.longArg(2, 0), "turn ms");
    }

    public void testInteractionVerbs() {
        var screenCmd = ShowcaseScriptParser.parseLine("screen lads", 9);
        check(screenCmd != null, "screen not null");
        assertEquals("screen", screenCmd.verb(), "screen verb");
        assertEquals("lads", screenCmd.arg(0, ""), "screen name");

        var cursorCmd1 = ShowcaseScriptParser.parseLine("cursor 100 200 500", 10);
        check(cursorCmd1 != null, "cursor not null");
        assertEquals("cursor", cursorCmd1.verb(), "cursor verb");
        assertEquals(100.0, cursorCmd1.doubleArg(0, 0), "cursor x");
        assertEquals(200.0, cursorCmd1.doubleArg(1, 0), "cursor y");
        assertEquals(500L, cursorCmd1.longArg(2, 0), "cursor ms");

        var cursorCmd2 = ShowcaseScriptParser.parseLine("cursor @Coordinates 600", 11);
        check(cursorCmd2 != null, "cursor @ not null");
        assertEquals("cursor", cursorCmd2.verb(), "cursor @ verb");
        assertEquals("@Coordinates", cursorCmd2.arg(0, ""), "cursor target");
        assertEquals(600L, cursorCmd2.longArg(1, 0), "cursor @ ms");

        var typeCmd = ShowcaseScriptParser.parseLine("type hello world 50", 12);
        check(typeCmd != null, "type not null");
        assertEquals("type", typeCmd.verb(), "type verb");

        var modCmd = ShowcaseScriptParser.parseLine("module Fullbright on", 13);
        check(modCmd != null, "mod not null");
        assertEquals("module", modCmd.verb(), "module verb");
        assertEquals("fullbright", modCmd.arg(0, "").toLowerCase(), "module name");
        assertEquals("on", modCmd.arg(1, ""), "module state");

        var setCmd = ShowcaseScriptParser.parseLine("set Coordinates Vertical false", 14);
        check(setCmd != null, "set not null");
        assertEquals("set", setCmd.verb(), "set verb");
        assertEquals("Coordinates", setCmd.arg(0, ""), "set module");
        assertEquals("Vertical", setCmd.arg(1, ""), "set setting");
        assertEquals("false", setCmd.arg(2, ""), "set value");

        var emoteCmd = ShowcaseScriptParser.parseLine("emote theladscore:groove", 15);
        check(emoteCmd != null, "emote not null");
        assertEquals("emote", emoteCmd.verb(), "emote verb");
        assertEquals("theladscore:groove", emoteCmd.arg(0, ""), "emote id");
    }

    public void testBatchLines() {
        List<String> script = List.of(
            "# Demo Script",
            "wait 1000",
            "",
            "mark test-start # inline comment",
            "look 0 0 500"
        );
        var cmds = ShowcaseScriptParser.parseLines(script);
        assertEquals(3, cmds.size(), "batch size");
        assertEquals("wait", cmds.get(0).verb(), "batch verb 0");
        assertEquals(2, cmds.get(0).lineNo(), "batch lineNo 0");
        assertEquals("mark", cmds.get(1).verb(), "batch verb 1");
        assertEquals("test-start", cmds.get(1).arg(0, ""), "batch arg 1");
        assertEquals("look", cmds.get(2).verb(), "batch verb 2");
    }

    public void testQuotedArgs() {
        var set = ShowcaseScriptParser.parseLine("set \"Wavy Capes\" Style Smooth", 1);
        assertEquals("Wavy Capes", set.arg(0, ""), "quoted module name");
        assertEquals("Smooth", set.arg(2, ""), "arg after quoted name");
        var cur = ShowcaseScriptParser.parseLine("cursor @\"Video Settings\" 1200", 2);
        assertEquals("@Video Settings", cur.arg(0, ""), "prefixed quoted widget");
        assertEquals(1200L, cur.longArg(1, 0), "duration after quoted widget");
    }

    public void testServerModeAndCommandRouting() {
        check(!ShowcaseDirector26.isServerMode(null), "null server mode");
        check(!ShowcaseDirector26.isServerMode(""), "empty server mode");
        check(ShowcaseDirector26.isServerMode("192.168.2.69:25590"), "valid server mode");
        assertEquals(ShowcaseDirector26.CommandRoute.INTEGRATED_SERVER, ShowcaseDirector26.routeCommand(false), "sp route");
        assertEquals(ShowcaseDirector26.CommandRoute.SERVER_CHAT, ShowcaseDirector26.routeCommand(true), "server route");

        assertEquals("gamemode creative", ShowcaseDirector26.formatPlayerCommand("/gamemode creative"), "formatPlayerCommand slash");
        assertEquals("mv tp showcase", ShowcaseDirector26.formatPlayerCommand("mv tp showcase"), "formatPlayerCommand no slash");
        assertEquals("time set noon", ShowcaseDirector26.formatPlayerCommand("   /time set noon  "), "formatPlayerCommand whitespace");

        assertEquals("tp @s 10.50 64.00 -20.25 90.0 -5.0", ShowcaseDirector26.formatTpCommand("@s", 10.5, 64.0, -20.25, 90.0f, -5.0f), "tp @s format");
        assertEquals("tp @a 0.00 80.00 0.00 0.0 0.0", ShowcaseDirector26.formatTpCommand("@a", 0.0, 80.0, 0.0, 0f, 0f), "tp @a format");
    }

    public void testSpotParsing() {
        double[] spot1 = ShowcaseDirector26.parseSpot("114.5 82.0 -467.5");
        assertEquals(114.5, spot1[0], "spot1 x");
        assertEquals(82.0, spot1[1], "spot1 y");
        assertEquals(-467.5, spot1[2], "spot1 z");

        double[] spot2 = ShowcaseDirector26.parseSpot("100, 64, 200");
        assertEquals(100.0, spot2[0], "spot2 x");
        assertEquals(64.0, spot2[1], "spot2 y");
        assertEquals(200.0, spot2[2], "spot2 z");

        double[] spotDefault = ShowcaseDirector26.parseSpot(null);
        assertEquals(0.0, spotDefault[0], "default spot x");
        assertEquals(80.0, spotDefault[1], "default spot y");
        assertEquals(0.0, spotDefault[2], "default spot z");

        double[] spotEmpty = ShowcaseDirector26.parseSpot("   ");
        assertEquals(0.0, spotEmpty[0], "empty spot x");
        assertEquals(80.0, spotEmpty[1], "empty spot y");
        assertEquals(0.0, spotEmpty[2], "empty spot z");
    }

    public void testWindowTitleResolution() {
        assertEquals("Lads Feature Showcase", ShowcaseDirector26.resolveWindowTitle(false, "QA_Player"), "primary title");
        assertEquals("Lads Feature Showcase", ShowcaseDirector26.resolveWindowTitle(false, null), "primary title null");
        assertEquals("Lads Feature Showcase (QA_Player2)", ShowcaseDirector26.resolveWindowTitle(true, "QA_Player2"), "secondary title");
        assertEquals("Lads Feature Showcase (Player)", ShowcaseDirector26.resolveWindowTitle(true, null), "secondary fallback null");
        assertEquals("Lads Feature Showcase (Player)", ShowcaseDirector26.resolveWindowTitle(true, "  "), "secondary fallback empty");
    }

    public void testResetCommandBuilding() {
        var serverReset = ShowcaseDirector26.buildResetCommands(true, 100.0, 64.0, 200.0);
        check(serverReset.contains("kill @e[type=!player]"), "server reset kill");
        check(serverReset.contains("clear @s"), "server reset clear");
        check(serverReset.contains("time set noon"), "server reset time");
        check(serverReset.contains("weather clear"), "server reset weather");
        check(serverReset.contains("tp @s 100.00 64.00 200.00 0.0 0.0"), "server reset tp");

        var spReset = ShowcaseDirector26.buildResetCommands(false, 100.0, 64.0, 200.0);
        check(spReset.contains("kill @e[type=!player]"), "sp reset kill");
        check(spReset.contains("clear @a"), "sp reset clear");
        check(spReset.contains("time set noon"), "sp reset time");
        check(spReset.contains("weather clear"), "sp reset weather");
        check(spReset.contains("tp @a 100.00 64.00 200.00 0.0 0.0"), "sp reset tp");
    }
}
