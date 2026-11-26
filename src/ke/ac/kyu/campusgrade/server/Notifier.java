package ke.ac.kyu.campusgrade.server;

import ke.ac.kyu.campusgrade.common.EventLog;
import ke.ac.kyu.campusgrade.server.SubscriptionManager.Subscription;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sends "marks published" callbacks from its own thread pool (6.3).
 *
 * <p>publishMarks hands the work here and returns at once, holding no lock.
 * With 20 threads and 50 ms per callback, 2,000 students are told in about
 * 2000 / 20 x 50 ms = 5 s instead of 100 s one by one (2.2). A listener that
 * cannot be reached is logged, removed, and does not stop the others (6.2).
 */
public final class Notifier implements AutoCloseable {

    /** Summary of one publication, for logs and tests. */
    public record PublishReport(String unitCode, int targets, int delivered, int failed,
                                List<String> removed, long elapsedMillis) { }

    private final ExecutorService pool;
    private final SubscriptionManager subscriptions;
    private final EventLog log;

    public Notifier(int threads, SubscriptionManager subscriptions, EventLog log) {
        this.subscriptions = subscriptions;
        this.log = log;
        AtomicInteger n = new AtomicInteger();
        ThreadFactory tf = r -> {
            Thread t = new Thread(r, "notifier-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
        this.pool = Executors.newFixedThreadPool(threads, tf);
    }

    public CompletableFuture<PublishReport> publish(String unitCode, List<Subscription> targets) {
        long start = System.nanoTime();
        AtomicInteger delivered = new AtomicInteger();
        List<String> removed = java.util.Collections.synchronizedList(new ArrayList<>());
        List<CompletableFuture<Void>> calls = new ArrayList<>(targets.size());
        for (Subscription s : targets) {
            calls.add(CompletableFuture.runAsync(() -> {
                try {
                    s.listener().markPublished(unitCode);   // remote callback, no lock held
                    delivered.incrementAndGet();
                } catch (RemoteException | RuntimeException e) {
                    if (subscriptions.remove(s)) {
                        removed.add(s.studentId());
                    }
                    log.failure("callback markPublished(" + unitCode + ") to " + s.studentId()
                            + " failed; listener removed", e);
                }
            }, pool));
        }
        return CompletableFuture.allOf(calls.toArray(CompletableFuture<?>[]::new)).thenApply(v -> {
            long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            PublishReport r = new PublishReport(unitCode, targets.size(), delivered.get(),
                    targets.size() - delivered.get(), List.copyOf(removed), ms);
            log.info("publish " + unitCode + " finished: delivered " + r.delivered() + "/" + r.targets()
                    + ", removed " + r.removed() + ", " + ms + " ms");
            return r;
        });
    }

    @Override
    public void close() {
        pool.shutdownNow();
    }
}
