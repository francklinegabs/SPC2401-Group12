package ke.ac.kyu.campusgrade.client;

import ke.ac.kyu.campusgrade.api.GradeException;
import ke.ac.kyu.campusgrade.api.Lease;
import ke.ac.kyu.campusgrade.api.NotSubscribedException;
import ke.ac.kyu.campusgrade.api.StudentListener;
import ke.ac.kyu.campusgrade.common.Config;
import ke.ac.kyu.campusgrade.common.EventLog;
import ke.ac.kyu.campusgrade.common.KyuClientSocketFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Student client. Exports a {@link StudentListener}, subscribes with a lease,
 * renews the lease at half its length, and on "marks published" fetches the
 * mark.
 *
 * <p>By default the mark is fetched INSIDE markPublished(), which is exactly
 * the pattern that deadlocks the Section 4 code. It works here because the
 * server holds no lock while calling back (the 4.2 fix).
 *
 * <pre>
 * java ... StudentClient KYU001 [--units SPC2401,SPC2402] [--lease 60000] [--exit-after 1] [--no-console]
 * </pre>
 */
public final class StudentClient implements StudentListener, AutoCloseable {

    private final String studentId;
    private final RetryingCaller rmi;
    private final EventLog log;
    private final long requestedLease;
    private final ScheduledExecutorService renewer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "lease-renewer");
        t.setDaemon(true);
        return t;
    });
    private final Set<String> publishedUnits = ConcurrentHashMap.newKeySet();
    /** Units this student takes; pulled after a lost lease to catch missed notifications. */
    private final java.util.List<String> myUnits = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final CountDownLatch notifications;
    private StudentListener selfStub;

    public StudentClient(String studentId, RetryingCaller rmi, EventLog log, long requestedLease, int exitAfter) {
        this.studentId = studentId;
        this.rmi = rmi;
        this.log = log;
        this.requestedLease = requestedLease;
        this.notifications = new CountDownLatch(Math.max(exitAfter, 0));
    }

    /** Exports the callback object and subscribes. */
    public Lease start() throws Exception {
        selfStub = (StudentListener) UnicastRemoteObject.exportObject(this, 0,
                new KyuClientSocketFactory(), null);
        Lease lease = subscribe();
        long period = Math.max(100, lease.getDurationMillis() / 2);
        renewer.scheduleAtFixedRate(this::renew, period, period, TimeUnit.MILLISECONDS);
        return lease;
    }

    private Lease subscribe() throws GradeException, RetryingCaller.GaveUpException {
        Lease lease = rmi.call("subscribe(" + studentId + ")",
                s -> s.subscribe(studentId, selfStub, requestedLease));
        log.info("SUBSCRIBED " + lease);
        return lease;
    }

    /** Renews the lease. If it already expired (e.g. laptop slept 2 days), subscribe again. */
    private void renew() {
        try {
            rmi.call("renewLease(" + studentId + ")", s -> s.renewLease(studentId));
        } catch (NotSubscribedException e) {
            log.warn("lease was lost (" + e.getMessage() + "); re-subscribing and checking for missed marks");
            try {
                subscribe();
                // Notifications are not queued for absent students, so pull what may have been missed.
                for (String unit : myUnits) {
                    fetch(unit);
                }
            } catch (Exception ex) {
                log.failure("re-subscribe failed", ex);
            }
        } catch (Exception e) {
            log.failure("lease renewal failed; will try again next period", e);
        }
    }

    /** Remote callback, executed on an RMI thread in this JVM. */
    @Override
    public void markPublished(String unitCode) throws RemoteException {
        log.info("NOTIFIED marks published for " + unitCode);
        publishedUnits.add(unitCode);
        fetch(unitCode);                          // calls back into the server: no deadlock
        notifications.countDown();
    }

    public void fetch(String unitCode) {
        try {
            int mark = rmi.call("getMark(" + studentId + "," + unitCode + ")", s -> s.getMark(studentId, unitCode));
            log.info("MARK " + studentId + " " + unitCode + " = " + mark);
        } catch (GradeException e) {
            log.warn("REFUSED " + e.getClass().getSimpleName() + ": " + e.getMessage());
        } catch (RetryingCaller.GaveUpException e) {
            log.failure("GAVE UP " + e.getMessage(), e.getCause() == null ? e : e.getCause());
        }
    }

    public void setUnits(java.util.Collection<String> units) {
        myUnits.clear();
        myUnits.addAll(units);
    }

    public void awaitNotifications() throws InterruptedException {
        notifications.await();
    }

    public Set<String> publishedUnits() {
        return publishedUnits;
    }

    @Override
    public void close() {
        renewer.shutdownNow();
        try {
            rmi.call("unsubscribe(" + studentId + ")", s -> {
                s.unsubscribe(studentId);
                return null;
            });
            log.info("UNSUBSCRIBED " + studentId);
        } catch (Exception e) {
            log.failure("unsubscribe failed (lease will expire on the server)", e);
        }
        try {
            UnicastRemoteObject.unexportObject(this, true);
        } catch (NoSuchObjectException ignored) {
            // not exported
        }
        rmi.close();
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: StudentClient <studentId> [--units U1,U2] [--lease ms] [--exit-after n] [--no-console]");
            System.exit(2);
        }
        String callbackHost = Config.get(Config.CALLBACK_HOSTNAME, null);
        if (callbackHost != null) {
            // address the server will use to call this client back
            System.setProperty("java.rmi.server.hostname", callbackHost);
        }
        String id = args[0];
        long lease = 0;
        int exitAfter = 0;
        boolean console = true;
        java.util.List<String> units = java.util.List.of();
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--lease" -> lease = Long.parseLong(args[++i]);
                case "--units" -> units = java.util.List.of(args[++i].split(","));
                case "--exit-after" -> exitAfter = Integer.parseInt(args[++i]);
                case "--no-console" -> console = false;
                default -> throw new IllegalArgumentException("unknown option " + args[i]);
            }
        }
        EventLog log = new EventLog("student-" + id);
        StudentClient c = new StudentClient(id, new RetryingCaller(log), log, lease, exitAfter);
        c.setUnits(units);
        c.start();
        if (exitAfter > 0) {
            c.awaitNotifications();
            log.info("received " + exitAfter + " notification(s); exiting");
        } else if (console) {
            System.out.println("Student " + id + " subscribed. Commands: get <unit> | quit");
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
            String line;
            while ((line = in.readLine()) != null) {
                String[] p = line.trim().split("\\s+");
                if (p[0].equals("quit") || p[0].equals("exit")) {
                    break;
                } else if (p[0].equals("get") && p.length > 1) {
                    c.fetch(p[1]);
                } else if (!p[0].isEmpty()) {
                    System.out.println("commands: get <unit> | quit");
                }
            }
        } else {
            new CountDownLatch(1).await();     // wait for notifications until killed
        }
        c.close();
        System.exit(0);
    }
}
