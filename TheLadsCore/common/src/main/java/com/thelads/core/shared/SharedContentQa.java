package com.thelads.core.shared;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only ({@code -Dthelads.verifySharedContent=true}): names, sandbox guard and checks shared by every version's
 * shared-content probe. {@code -Dthelads.sharedContentRole=create|observe} with {@code -Dthelads.sharedContentRunId=<id>}
 * adds the disposable world, pack, shader pack and server entry checks.
 */
public final class SharedContentQa {
    public static final String FLAG = "thelads.verifySharedContent";
    public static final String ROLE = "thelads.sharedContentRole";
    public static final String RUN_ID = "thelads.sharedContentRunId";
    private static final Pattern RUN_ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,40}");
    private static final Logger LOG = LoggerFactory.getLogger("TheLadsCore");

    public enum Role { CHECK, CREATE, OBSERVE }

    @FunctionalInterface
    public interface Body {
        void run(SharedContentQa qa) throws Exception;
    }

    private Role role = Role.CHECK;
    private String runId = "";
    private int passed;

    SharedContentQa() {
    }

    public static boolean requested() {
        return Boolean.getBoolean(FLAG);
    }

    /** Runs a probe and logs the END or FAILED marker the verification harness waits for. */
    public static void run(Body body) {
        SharedContentQa qa = new SharedContentQa();
        try {
            qa.configure(System.getProperty(ROLE), System.getProperty(RUN_ID));
            LOG.info("Lads shared content probe BEGIN: role {}, run id '{}', {}={} (redirect {})", qa.role, qa.runId,
                SharedContentPaths.ENV, SharedContentPaths.root(), SharedContentPaths.redirectEnabled() ? "on" : "off");
            body.run(qa);
            LOG.info("Lads shared content probe END: {} passed, 0 failed", qa.passed);
        } catch (Throwable failure) {
            LOG.error("Lads shared content probe FAILED after {} checks", qa.passed, failure);
        }
    }

    void configure(String roleValue, String runIdValue) {
        role = switch (roleValue == null ? "" : roleValue.trim()) {
            case "" -> Role.CHECK;
            case "create" -> Role.CREATE;
            case "observe" -> Role.OBSERVE;
            default -> throw new IllegalArgumentException(ROLE + " must be 'create' or 'observe', not '" + roleValue + "'");
        };
        if (role != Role.CHECK && (runIdValue == null || !RUN_ID_PATTERN.matcher(runIdValue).matches())) {
            throw new IllegalArgumentException(RUN_ID + " must be 1-40 letters, digits, '-' or '_' for role " + roleValue);
        }
        runId = role == Role.CHECK ? "" : runIdValue;
    }

    public Role role() {
        return role;
    }

    public String runId() {
        return runId;
    }

    /** File name of both the disposable resource pack and the shader pack placeholder. */
    public String packFileName() {
        return "lads-shared-qa-" + runId + ".zip";
    }

    public String serverName() {
        return "Lads Shared QA " + runId;
    }

    public String serverIp() {
        return "lads-qa-" + runId + ".invalid";
    }

    public String worldName() {
        return "Lads Shared QA " + runId;
    }

    public int passed() {
        return passed;
    }

    public void check(boolean ok, String what) {
        if (!ok) {
            throw new IllegalStateException(what);
        }
        passed++;
    }

    public void info(String message, Object... arguments) {
        LOG.info("Lads shared content: " + message, arguments);
    }

    /** The game folder must be {@code <repo>/artifacts/verification/<name>} and the shared root must lie inside that folder. */
    public void requireSandbox(Path gameDir) throws IOException {
        Path game = gameDir.toRealPath();
        Path verification = game.getParent();
        Path artifacts = verification == null ? null : verification.getParent();
        check(artifacts != null && verification.getFileName().toString().equals("verification")
                && artifacts.getFileName().toString().equals("artifacts") && artifacts.getParent() != null
                && Files.isRegularFile(artifacts.getParent().resolve("TheLadsCore/settings.gradle")),
            "QA game folder is <repo>/artifacts/verification/<name>: " + game);
        check(SharedContentPaths.redirectEnabled(), SharedContentPaths.ENV + " is set to a valid absolute path");
        check(SharedContentPaths.redirectedInside(verification),
            "shared root " + SharedContentPaths.root() + " is a sandbox inside " + verification);
    }

    /** {@code path} must resolve (through links) to {@code expected}, which must lie inside the shared root. */
    public Path requireShared(String what, Path path, Path expected) throws IOException {
        Path real = path.toRealPath();
        check(real.equals(expected.toRealPath()) && real.startsWith(SharedContentPaths.root().toRealPath()),
            what + " " + path + " resolves to " + real + ", not to the shared " + expected);
        info("{} {} resolves to {}", what, path, real);
        return real;
    }

    /** servers.dat, once it exists, must be a file directly in the shared root (ServerList writes it there). */
    public void requireSharedServersFile() throws IOException {
        Path file = SharedContentPaths.serversFile();
        check(!Files.exists(file) || file.toRealPath().equals(SharedContentPaths.root().toRealPath().resolve("servers.dat")),
            "servers.dat " + file + " resolves into the shared root");
        info("server list file {} ({})", file, Files.exists(file) ? Files.size(file) + " bytes" : "not created yet");
    }
}
