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
}
