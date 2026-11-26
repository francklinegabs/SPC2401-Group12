package ke.ac.kyu.campusgrade.integration;

import ke.ac.kyu.campusgrade.api.GradeService;
import ke.ac.kyu.campusgrade.api.StudentListener;
import ke.ac.kyu.campusgrade.client.RetryingCaller;
import ke.ac.kyu.campusgrade.common.EventLog;
import ke.ac.kyu.campusgrade.common.KyuClientSocketFactory;
import ke.ac.kyu.campusgrade.server.GradeServer;
import ke.ac.kyu.campusgrade.server.GradeServiceImpl;
import ke.ac.kyu.campusgrade.server.ServerSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests over real Java RMI (registry, stubs, TCP) inside one JVM. */
class RmiIntegrationTest {

    private final List<AutoCloseable> cleanup = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final EventLog log = new EventLog("rmi-test", "evidence/logs/junit", false);

    @AfterEach
    void tearDown() throws Exception {
        for (int i = cleanup.size() - 1; i >= 0; i--) {
            cleanup.get(i).close();
        }
    }

    static int freePort() throws Exception {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private GradeServer startServer(int port, ServerSettings st) throws Exception {
        GradeServiceImpl impl = new GradeServiceImpl(st, log);
        GradeServer server = GradeServer.start(impl, "localhost", port, true, 0, log);
        cleanup.add(server);
        return server;
    }

    private static ServerSettings settings() {
        ServerSettings s = new ServerSettings();
        s.workers = 8;
        s.notifierThreads = 8;
        s.journalFile = null;
        return s;
    }

    @Test
    @DisplayName("I01 25 clients submit at once over RMI: no lost update, no deadlock")
    void concurrentClients() throws Exception {
        int port = freePort();
        GradeServer server = startServer(port, settings());
        int clients = 25;
        ExecutorService pool = Executors.newFixedThreadPool(clients);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> fs = new ArrayList<>();
        long t0 = System.nanoTime();
        for (int c = 0; c < clients; c++) {
            int id = c;
            fs.add(pool.submit(() -> {
                try (RetryingCaller rmi = new RetryingCaller("localhost", port, 10_000, 2, 100, log)) {
                    go.await();
                    String sid = String.format("KYU%03d", id + 1);
                    rmi.call("submit", s -> {
                        s.submitMark("own-" + id, sid, "SPC2401", 40 + id);
                        return null;
                    });
                    rmi.call("submit-shared", s -> {       // all 25 write the SAME key
                        s.submitMark("shared-" + id, "KYU999", "SPC2402", id);
                        return null;
                    });
                }
                return null;
            }));
        }
        go.countDown();
        for (Future<?> f : fs) {
            f.get(30, TimeUnit.SECONDS);   // a deadlock would time out here
        }
        pool.shutdown();
        long ms = (System.nanoTime() - t0) / 1_000_000;
        GradeServiceImpl impl = server.impl();
        assertEquals(2L * clients, impl.submittedCount());
        assertEquals(clients, impl.listMarks("SPC2401").size());
        assertEquals(1, impl.listMarks("SPC2402").size());
        System.out.println("[I01] " + clients + " RMI clients x 2 submits done in " + ms
                + " ms; submitted=" + impl.submittedCount() + ", SPC2401 marks="
                + impl.listMarks("SPC2401").size() + ", SPC2402 marks=" + impl.listMarks("SPC2402").size());
    }

    /** A real exported listener that calls getMark back on the server before returning. */
    public static final class CallingBackListener implements StudentListener {
        final GradeService server;
        final CompletableFuture<Integer> got = new CompletableFuture<>();

        CallingBackListener(GradeService server) {
            this.server = server;
        }

        @Override
        public void markPublished(String unitCode) {
            try {
                got.complete(server.getMark("KYU001", unitCode));
            } catch (Exception e) {
                got.completeExceptionally(e);
            }
        }
    }

    @Test
    @DisplayName("I02 4.2 fix over RMI: markPublished() calls getMark() and gets 78")
    void callbackCallsGetMark() throws Exception {
        int port = freePort();
        startServer(port, settings());
        GradeService stub = (GradeService) LocateRegistry.getRegistry("localhost", port)
                .lookup(GradeService.SERVICE_NAME);
        CallingBackListener l = new CallingBackListener(stub);
        StudentListener lStub = (StudentListener) UnicastRemoteObject.exportObject(l, 0,
                new KyuClientSocketFactory(), null);
        cleanup.add(() -> UnicastRemoteObject.unexportObject(l, true));
        stub.subscribe("KYU001", lStub);
        stub.submitMark("KYU001", "SPC2401", 78);
        stub.publishMarks("SPC2401");
        assertEquals(78, l.got.get(10, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("I03 no server: client retries with back-off, logs each failure, then gives up")
    void noServerGivesUp() throws Exception {
        int port = freePort();     // nothing listens here
        try (RetryingCaller rmi = new RetryingCaller("localhost", port, 2_000, 3, 100, log)) {
            long t0 = System.nanoTime();
            assertThrows(RetryingCaller.GaveUpException.class,
                    () -> rmi.call("getMark", s -> s.getMark("KYU001", "SPC2401")));
            long ms = (System.nanoTime() - t0) / 1_000_000;
            assertTrue(ms >= 100 + 200 + 400, "back-off 100+200+400 ms expected, took " + ms);
        }
    }

    @Test
    @DisplayName("I04 server comes up while the client is retrying: call succeeds on a later attempt")
    void serverAppearsDuringRetry() throws Exception {
        int port = freePort();
        CompletableFuture<Void> starter = CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(400);
                startServer(port, settings()).impl().submitMark("KYU001", "SPC2401", 66);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        // 8 retries with back-off 200 ms doubling = about 51 s of patience, plenty for a slow start
        try (RetryingCaller rmi = new RetryingCaller("localhost", port, 5_000, 8, 200, log)) {
            List<?> rows = rmi.call("listMarks", s -> s.listMarks("SPC2401"));
            starter.get(30, TimeUnit.SECONDS);
            assertTrue(rows.size() <= 1);
        }
    }

    @Test
    @DisplayName("I05 every attempt times out, yet the request ID makes the mark apply exactly once")
    void timeoutRetryAppliesOnce() throws Exception {
        int port = freePort();
        ServerSettings st = settings();
        st.simDiskMillis = 1_200;          // each request takes 1.2 s on the server
        GradeServer server = startServer(port, st);
        try (RetryingCaller rmi = new RetryingCaller("localhost", port, 500, 2, 50, log)) {
            assertThrows(RetryingCaller.GaveUpException.class, () -> rmi.call("submitMark", s -> {
                s.submitMark("fixed-req-1", "KYU001", "SPC2401", 78);
                return null;
            }));
        }
        // Wait until the original attempt is applied, then long enough for every orphaned
        // retry (each needs 1.2 s of server work) to arrive and be judged a duplicate.
        long deadline = System.currentTimeMillis() + 15_000;
        while (server.impl().submittedCount() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        Thread.sleep(4_000);
        assertEquals(1, server.impl().submittedCount());
        assertEquals(78, server.impl().listMarks("SPC2401").get(0).getMark());
    }
}
