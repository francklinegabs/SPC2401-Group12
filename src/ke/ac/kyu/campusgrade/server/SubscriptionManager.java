package ke.ac.kyu.campusgrade.server;

import ke.ac.kyu.campusgrade.api.Lease;
import ke.ac.kyu.campusgrade.api.NotSubscribedException;
import ke.ac.kyu.campusgrade.api.StudentListener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Leased subscriptions. A subscription lives only while its lease is renewed;
 * a client that crashes or sleeps stops renewing and is reaped, so the server
 * never keeps calling dead listeners forever.
 */
public final class SubscriptionManager {

    /** One subscriber. The listener is a remote reference (a stub) into the student's JVM. */
    public static final class Subscription {
        private final String studentId;
        private final StudentListener listener;
        private final long durationMillis;
        private volatile long expiresAtMillis;

        Subscription(String studentId, StudentListener listener, long durationMillis, long expiresAtMillis) {
            this.studentId = studentId;
            this.listener = listener;
            this.durationMillis = durationMillis;
            this.expiresAtMillis = expiresAtMillis;
        }

        public String studentId() { return studentId; }
        public StudentListener listener() { return listener; }
        public long expiresAtMillis() { return expiresAtMillis; }

        Lease toLease() {
            return new Lease(studentId, durationMillis, expiresAtMillis);
        }
    }

    private final Map<String, Subscription> subs = new ConcurrentHashMap<>();
    private final long defaultLeaseMillis;
    private final long maxLeaseMillis;
    private final LongSupplier clock;

    public SubscriptionManager(long defaultLeaseMillis, long maxLeaseMillis, LongSupplier clock) {
        this.defaultLeaseMillis = defaultLeaseMillis;
        this.maxLeaseMillis = maxLeaseMillis;
        this.clock = clock;
    }

    /** Subscribes (or re-subscribes, replacing an old listener). */
    public Lease subscribe(String studentId, StudentListener listener, long requestedMillis) {
        long granted = requestedMillis <= 0 ? defaultLeaseMillis : Math.min(requestedMillis, maxLeaseMillis);
        Subscription s = new Subscription(studentId, listener, granted, clock.getAsLong() + granted);
        subs.put(studentId, s);
        return s.toLease();
    }

    public Lease renew(String studentId) throws NotSubscribedException {
        Subscription s = subs.get(studentId);
        long now = clock.getAsLong();
        if (s == null || s.expiresAtMillis < now) {
            if (s != null) {
                subs.remove(studentId, s);
            }
            throw new NotSubscribedException("No live subscription for " + studentId
                    + " (lease expired or never subscribed); subscribe again");
        }
        s.expiresAtMillis = now + s.durationMillis;
        return s.toLease();
    }

    public boolean unsubscribe(String studentId) {
        return subs.remove(studentId) != null;
    }

    /** Removes this exact subscription (not a newer one for the same student). */
    public boolean remove(Subscription s) {
        return subs.remove(s.studentId, s);
    }

    /** Live subscriptions for the given students, as a snapshot list. */
    public List<Subscription> activeFor(Collection<String> studentIds) {
        long now = clock.getAsLong();
        List<Subscription> out = new ArrayList<>();
        for (String sid : studentIds) {
            Subscription s = subs.get(sid);
            if (s != null && s.expiresAtMillis >= now) {
                out.add(s);
            }
        }
        return out;
    }

    /** Removes every expired lease and returns the affected student IDs. */
    public List<String> reapExpired() {
        long now = clock.getAsLong();
        List<String> removed = new ArrayList<>();
        for (Subscription s : subs.values()) {
            if (s.expiresAtMillis < now && subs.remove(s.studentId, s)) {
                removed.add(s.studentId);
            }
        }
        return removed;
    }

    public boolean isSubscribed(String studentId) {
        Subscription s = subs.get(studentId);
        return s != null && s.expiresAtMillis >= clock.getAsLong();
    }

    public int size() {
        return subs.size();
    }
}
