package ke.ac.kyu.campusgrade.experiments;

import ke.ac.kyu.campusgrade.api.StudentListener;
import ke.ac.kyu.campusgrade.common.EventLog;
import ke.ac.kyu.campusgrade.server.Notifier;
import ke.ac.kyu.campusgrade.server.SubscriptionManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Measurements behind 2.2 and 3.4.
 * <ul>
 *   <li>2.2: 2,000 subscribers, each callback takes 50 ms, notifier with 1 and 20 threads;</li>
 *   <li>3.4: cost of creating a thread vs creating (starting) a process.</li>
 * </ul>
 */
public final class SizingExperiments {

    private static final Path OUT = Path.of("evidence", "perf");

    public static void main(String[] args) throws Exception {
        Files.createDirectories(OUT);
        StringBuilder csv = new StringBuilder("notifier_threads,students,callback_ms,calculated_s,measured_s\n");
        for (int threads : new int[]{20, 1}) {
            csv.append(notify(threads, 2_000, 50));
        }
        Files.writeString(OUT.resolve("notifier_sizing.csv"), csv.toString());
        System.out.print(csv);
        String tp = threadVsProcess();
        Files.writeString(OUT.resolve("thread_vs_process.csv"), tp);
        System.out.print(tp);
        System.exit(0);
    }

    static String notify(int threads, int students, long callbackMs) throws Exception {
        SubscriptionManager subs = new SubscriptionManager(600_000, 600_000, System::currentTimeMillis);
        StudentListener slow = unit -> {
            long end = System.nanoTime() + callbackMs * 1_000_000L;   // exact 50 ms, no timer rounding
            while (System.nanoTime() < end) {
                try {
                    Thread.sleep(Math.max(0, (end - System.nanoTime()) / 1_000_000L - 1));
                } catch (InterruptedException e) {
                    return;
                }
            }
        };
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < students; i++) {
            String id = "KYU" + i;
            ids.add(id);
            subs.subscribe(id, slow, 0);
        }
        try (Notifier n = new Notifier(threads, subs, EventLog.silent("sizing"))) {
            long t0 = System.nanoTime();
            Notifier.PublishReport r = n.publish("SPC2401", subs.activeFor(ids)).get(10, TimeUnit.MINUTES);
            double s = (System.nanoTime() - t0) / 1e9;
            double calc = Math.ceil((double) students / threads) * callbackMs / 1000.0;
            if (r.delivered() != students) {
                throw new IllegalStateException("delivered " + r.delivered());
            }
            return String.format(Locale.ROOT, "%d,%d,%d,%.1f,%.2f%n", threads, students, callbackMs, calc, s);
        }
    }

    static String threadVsProcess() throws Exception {
        int n = 2_000;
        for (int i = 0; i < 200; i++) {                       // warm-up
            Thread t = new Thread(() -> { });
            t.start();
            t.join();
        }
        long t0 = System.nanoTime();
        for (int i = 0; i < n; i++) {
            Thread t = new Thread(() -> { });
            t.start();
            t.join();
        }
        double threadUs = (System.nanoTime() - t0) / 1e3 / n;

        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        int m = 10;
        new ProcessBuilder(java, "-version").redirectErrorStream(true).start().waitFor();
        t0 = System.nanoTime();
        for (int i = 0; i < m; i++) {
            new ProcessBuilder(java, "-version").redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start().waitFor();
        }
        double procMs = (System.nanoTime() - t0) / 1e6 / m;
        return "what,mean\n"
                + String.format(Locale.ROOT, "create+start+join a thread,%.1f us%n", threadUs)
                + String.format(Locale.ROOT, "start a new JVM process (java -version) and wait,%.1f ms%n", procMs)
                + String.format(Locale.ROOT, "ratio,%.0fx%n", procMs * 1000 / threadUs);
    }
}
