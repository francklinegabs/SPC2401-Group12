package ke.ac.kyu.campusgrade.integration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Starts a KYUCampusGrade program in its own JVM process and watches its output. */
public final class Proc implements AutoCloseable {

    private final Process process;
    private final Path out;
    private final String name;

    private Proc(String name, Process process, Path out) {
        this.name = name;
        this.process = process;
        this.out = out;
    }

    /**
     * @param props  "-Dkyu.x=y" style JVM options
     * @param main   fully qualified main class
     * @param args   program arguments
     */
    public static Proc start(String name, Path outDir, List<String> props, String main, String... args)
            throws IOException {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        List<String> cmd = new ArrayList<>();
        cmd.add(java);
        cmd.add("-cp");
        cmd.add(classPath());
        cmd.addAll(props);
        cmd.add(main);
        cmd.addAll(List.of(args));
        Files.createDirectories(outDir);
        Path out = outDir.resolve(name + ".out");
        Process p = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .redirectOutput(out.toFile())
                .redirectInput(ProcessBuilder.Redirect.from(nullFile()))
                .start();
        return new Proc(name, p, out);
    }

    /** Directory or jar holding the compiled application classes (works under any launcher). */
    static String classPath() {
        try {
            return Path.of(ke.ac.kyu.campusgrade.server.GradeServer.class
                    .getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        } catch (java.net.URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private static File nullFile() {
        return new File(System.getProperty("os.name").startsWith("Windows") ? "NUL" : "/dev/null");
    }

    public String output() {
        try {
            return Files.exists(out) ? Files.readString(out, StandardCharsets.UTF_8) : "";
        } catch (IOException e) {
            return "";
        }
    }

    /** Waits until the output contains {@code text}. */
    public boolean awaitOutput(String text, long timeoutMillis) throws InterruptedException {
        long end = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < end) {
            if (output().contains(text)) {
                return true;
            }
            if (!process.isAlive() && !output().contains(text)) {
                return false;
            }
            Thread.sleep(50);
        }
        return false;
    }

    public boolean awaitExit(long timeoutMillis) throws InterruptedException {
        return process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    public int exitCode() {
        return process.exitValue();
    }

    public boolean isAlive() {
        return process.isAlive();
    }

    /** Kills the JVM at once, like pulling the plug: no shutdown hook, no unsubscribe. */
    public void kill() {
        process.destroyForcibly();
        try {
            process.waitFor(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public String name() {
        return name;
    }

    public Path outFile() {
        return out;
    }

    @Override
    public void close() {
        if (process.isAlive()) {
            kill();
        }
    }
}
