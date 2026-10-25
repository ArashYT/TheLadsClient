package com.thelads.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 1.11.0 QA R2-F1: the Lads menu lists only the modules a version registers as built in (ModuleSupport), so a module whose code runs
 * on a version but is never registered there stays hidden. Each row: how the version's Qol file names the module, and a call that
 * exists only where the module's code runs. A new lane module gets a row here.
 */
class LaneModuleSupportTest {
    private static final String[][] LANES = {
        {"ContainerSortingModule.NAME", "ContainerSorting.isAllowed("},
        {"CreativeFlyBoostModule.NAME", "CreativeFlyBoost.isAllowed("},
        {"ElytraSwapModule.NAME", "ElytraSwap.isAllowed("},
        {"LinkPreviewModule.NAME", "LinkPreview."},
        {"MlgCobwebModule.NAME", "MlgClutchHelper."},
        {"SchematicManager.NAME", "SchematicManager.draw("},
        {"\"Server Notification Actions\"", "ServerRulesNotice.mouseClicked("},
        {"SkyBlockSuite.MODULE_NAME", "TerminalSolver."},
        {"BedColorsModule.NAME", "BedColorsModule.isActive("}};
    /** Code present but registered unavailable on purpose: Bed Colors on 1.8.9 (QA R2-F5, other team colors come out nearly black). */
    private static final List<String> NOT_READY = List.of("v1_8_9 BedColorsModule.NAME");

    @Test void laneModulesAreBuiltInExactlyWhereTheirCodeRuns() throws Exception {
        Path root = Path.of("..").toAbsolutePath().normalize();
        List<String> wrong = new ArrayList<>();
        for (String[] version : new String[][] {{"v1_8_9", "v1_8_9/qol/Qol189.java"}, {"v1_20_1", "v1_20_1/qol/Qol1201.java"},
                {"v26_3", "v26_2/qol/Qol26.java"}}) {
            Path main = root.resolve(version[0] + "/src/main/java");
            StringBuilder code = new StringBuilder();
            try (Stream<Path> files = Files.walk(main)) {
                for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) code.append(Files.readString(file));
            }
            String qol = Files.readString(main.resolve("com/thelads/core/" + version[1]));
            for (String[] lane : LANES) {
                boolean runs = code.indexOf(lane[1]) >= 0, notReady = NOT_READY.contains(version[0] + " " + lane[0]);
                boolean unavailable = Pattern.compile("registerUnavailable\\([\\w.]*" + Pattern.quote(lane[0])).matcher(qol).find();
                boolean builtIn = qol.contains(lane[0]) && !unavailable;
                boolean ok = !runs ? !builtIn : notReady ? unavailable : builtIn;
                if (!ok) wrong.add(version[0] + " " + lane[0] + (!runs ? " has no code there but is built in" : notReady
                    ? " should be registered unavailable" : " runs there but is not registered built in"));
            }
        }
        assertEquals(List.of(), wrong);
    }
}
