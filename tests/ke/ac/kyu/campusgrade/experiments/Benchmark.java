package ke.ac.kyu.campusgrade.experiments;

import ke.ac.kyu.campusgrade.api.GradeService;
import ke.ac.kyu.campusgrade.common.EventLog;
import ke.ac.kyu.campusgrade.server.GradeServer;
import ke.ac.kyu.campusgrade.server.GradeServiceImpl;
import ke.ac.kyu.campusgrade.server.ServerSettings;

import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.rmi.registry.LocateRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

/**
 * Section 7.4 measurements. Writes CSV files to evidence/perf/; the graphs are
 * drawn by docs/plot_perf.py.
 * <ol>
 *   <li>local call vs remote (RMI loopback) call: mean latency and throughput;</li>
 *   <li>1, 2, 4, 8, 16 worker threads with 4 ms CPU + 6 ms disk per request
 *       (the 4.3 workload), on one simulated core and on all real cores;</li>
 *   <li>the 4.4 dashboard: 3 calls one after another vs concurrently.</li>
 * </ol>
 */
public final class Benchmark {

    private static final Path OUT = Path.of("evidence", "perf");
    private static final EventLog LOG = EventLog.silent("bench");

    public static void main(String[] args) throws Exception {
        Files.createDirectories(OUT);
        int[] workers = {1, 2, 4, 8, 16};
        long seconds = args.length > 0 ? Long.parseLong(args[0]) : 5;

        StringBuilder env = new StringBuilder();
        env.append("java=").append(System.getProperty("java.version"))
                .append(", os=").append(System.getProperty("os.name"))
                .append(", cores=").append(Runtime.getRuntime().availableProcessors()).append('\n');
        for (int ms : new int[]{2, 6}) {          // timer granularity explains part of 7.4
            long t = System.nanoTime();
            for (int i = 0; i < 300; i++) {
                Thread.sleep(ms);
            }
            env.append(String.format(Locale.ROOT, "Thread.sleep(%d) really takes %.2f ms on average%n",
                    ms, (System.nanoTime() - t) / 1e6 / 300));
        }
        Files.writeString(OUT.resolve("environment.txt"), env.toString());
        System.out.print(env);

        localVsRemote();
        StringBuilder csv = new StringBuilder("mode,workers,throughput_rps,mean_latency_ms,expected_rps\n");
        for (int w : workers) {
            csv.append(threads(w, 1, seconds, "one-core"));
        }
        for (int w : workers) {
            csv.append(threads(w, 0, seconds, "all-cores"));
        }
        Files.writeString(OUT.resolve("threads.csv"), csv.toString(), StandardCharsets.UTF_8);
        dashboard();
        System.exit(0);
    }

    // ------------------------------------------------------------------ 1

    static void localVsRemote() throws Exception {
        ServerSettings st = plain();
        st.workers = 0;                      // measure pure invocation cost, no hand-off
        int port = freePort();
        try (GradeServer server = GradeServer.start(new GradeServiceImpl(st, LOG), "localhost", port, true, 0, LOG)) {
            GradeServiceImpl local = server.impl();
            local.submitMark("KYU001", "SPC2401", 78);
            local.publishMarks("SPC2401");
            GradeService remote = (GradeService) LocateRegistry.getRegistry("localhost", port)
                    .lookup(GradeService.SERVICE_NAME);

            int localN = 2_000_000;
            int remoteN = 20_000;
            for (int i = 0; i < 200_000; i++) local.getMark("KYU001", "SPC2401");     // warm-up
            for (int i = 0; i < 3_000; i++) remote.getMark("KYU001", "SPC2401");

            long t0 = System.nanoTime();
            for (int i = 0; i < localN; i++) local.getMark("KYU001", "SPC2401");
            long localNs = System.nanoTime() - t0;

            t0 = System.nanoTime();
            for (int i = 0; i < remoteN; i++) remote.getMark("KYU001", "SPC2401");
            long remoteNs = System.nanoTime() - t0;

            double localUs = localNs / 1e3 / localN;
            double remoteUs = remoteNs / 1e3 / remoteN;
            String csv = "call,iterations,mean_latency_us,throughput_per_s\n"
                    + String.format(Locale.ROOT, "local,%d,%.4f,%.0f%n", localN, localUs, 1e6 / localUs)
                    + String.format(Locale.ROOT, "remote,%d,%.2f,%.0f%n", remoteN, remoteUs, 1e6 / remoteUs);
            Files.writeString(OUT.resolve("local_vs_remote.csv"), csv);
            System.out.print(csv);
            System.out.printf(Locale.ROOT, "remote/local = %.0fx%n", remoteUs / localUs);
        }
    }

    // ------------------------------------------------------------------ 2

    static String threads(int workers, int simCores, long seconds, String mode) throws Exception {
        ServerSettings st = plain();
        st.workers = workers;
        st.simCpuMillis = 4;
        st.simDiskMillis = 6;
        st.simCores = simCores;
        int port = freePort();
        try (GradeServer server = GradeServer.start(new GradeServiceImpl(st, LOG), "localhost", port, true, 0, LOG)) {
            server.impl().submitMark("KYU001", "SPC2401", 78);
            server.impl().publishMarks("SPC2401");
            GradeService remote = (GradeService) LocateRegistry.getRegistry("localhost", port)
                    .lookup(GradeService.SERVICE_NAME);
            int clients = 32;                // always more demand than workers
            ExecutorService pool = Executors.newFixedThreadPool(clients);
            AtomicBoolean measuring = new AtomicBoolean(false);
            AtomicBoolean stop = new AtomicBoolean(false);
            LongAdder done = new LongAdder();
            LongAdder latencyNs = new LongAdder();
            CountDownLatch started = new CountDownLatch(clients);
            List<Future<?>> fs = new ArrayList<>();
            for (int c = 0; c < clients; c++) {
                fs.add(pool.submit(() -> {
                    started.countDown();
                    while (!stop.get()) {
                        long t = System.nanoTime();
                        remote.getMark("KYU001", "SPC2401");
                        if (measuring.get()) {
                            latencyNs.add(System.nanoTime() - t);
                            done.increment();
                        }
                    }
                    return null;
                }));
            }
            started.await();
            Thread.sleep(1_000);             // warm-up
            measuring.set(true);
            long t0 = System.nanoTime();
            Thread.sleep(seconds * 1_000);
            measuring.set(false);
            long elapsed = System.nanoTime() - t0;
            stop.set(true);
            for (Future<?> f : fs) {
                f.get();
            }
            pool.shutdown();
            double rps = done.sum() / (elapsed / 1e9);
            double meanMs = latencyNs.sum() / 1e6 / Math.max(1, done.sum());
            double expected = simCores == 1 ? Math.min(workers * 100.0, 250.0)
                    : Math.min(workers * 100.0, Runtime.getRuntime().availableProcessors() * 250.0);
            String row = String.format(Locale.ROOT, "%s,%d,%.1f,%.2f,%.0f%n", mode, workers, rps, meanMs, expected);
            System.out.print(row);
            return row;
        }
    }

    // ------------------------------------------------------------------ 3

    /** 4.4: three independent calls, one after another vs concurrently. */
    static void dashboard() throws Exception {
        StringBuilder csv = new StringBuilder("net_delay_ms,mode,mean_ms\n");
        for (long delay : new long[]{0, 2}) {
            System.setProperty("kyu.net.delay.ms", Long.toString(delay));   // read by the socket factory
            ServerSettings st = plain();
            st.workers = 8;
            int port = freePort();
            // Own export port: RMI shares port 0 between exports and would reuse old
            // connections that were opened before the delay was switched on.
            int exportPort = freePort();
            try (GradeServer server = GradeServer.start(new GradeServiceImpl(st, LOG), "localhost", port, true,
                    exportPort, LOG)) {
                GradeServiceImpl impl = server.impl();
                impl.submitMark("KYU001", "SPC2401", 78);
                impl.submitMark("KYU001", "SPC2402", 66);
                impl.submitMark("KYU001", "SPC2403", 71);
                for (String u : List.of("SPC2401", "SPC2402", "SPC2403")) {
                    impl.publishMarks(u);
                }
                GradeService remote = (GradeService) LocateRegistry.getRegistry("localhost", port)
                        .lookup(GradeService.SERVICE_NAME);
                ExecutorService pool = Executors.newFixedThreadPool(3);
                int n = delay == 0 ? 3_000 : 400;
                for (int i = 0; i < 200; i++) sequential(remote);                   // warm-up
                for (int i = 0; i < 200; i++) concurrent(remote, pool);
                long t0 = System.nanoTime();
                for (int i = 0; i < n; i++) sequential(remote);
                double seq = (System.nanoTime() - t0) / 1e6 / n;
                t0 = System.nanoTime();
                for (int i = 0; i < n; i++) concurrent(remote, pool);
                double con = (System.nanoTime() - t0) / 1e6 / n;
                pool.shutdown();
                csv.append(String.format(Locale.ROOT, "%d,sequential,%.3f%n%d,concurrent,%.3f%n",
                        delay, seq, delay, con));
            }
        }
        System.clearProperty("kyu.net.delay.ms");
        Files.writeString(OUT.resolve("dashboard.csv"), csv.toString());
        System.out.print(csv);
    }

    private static void sequential(GradeService s) throws Exception {
        s.getMark("KYU001", "SPC2401");
        s.getMark("KYU001", "SPC2402");
        s.getMark("KYU001", "SPC2403");
    }

    private static void concurrent(GradeService s, ExecutorService pool) throws Exception {
        Future<Integer> a = pool.submit(() -> s.getMark("KYU001", "SPC2401"));
        Future<Integer> b = pool.submit(() -> s.getMark("KYU001", "SPC2402"));
        Future<Integer> c = pool.submit(() -> s.getMark("KYU001", "SPC2403"));
        a.get();
        b.get();
        c.get();
    }

    private static ServerSettings plain() {
        ServerSettings st = new ServerSettings();
        st.journalFile = null;
        st.notifierThreads = 2;
        return st;
    }

    private static int freePort() throws Exception {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }
}
