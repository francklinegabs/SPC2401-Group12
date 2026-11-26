package ke.ac.kyu.campusgrade.server;

import ke.ac.kyu.campusgrade.api.InvalidMarkException;
import ke.ac.kyu.campusgrade.api.MarkNotPublishedException;
import ke.ac.kyu.campusgrade.api.NotSubscribedException;
import ke.ac.kyu.campusgrade.api.UnknownStudentException;
import ke.ac.kyu.campusgrade.api.UnknownUnitException;
import ke.ac.kyu.campusgrade.common.EventLog;
import ke.ac.kyu.campusgrade.server.TestSupport.DeadListener;
import ke.ac.kyu.campusgrade.server.TestSupport.ManualClock;
import ke.ac.kyu.campusgrade.server.TestSupport.RecordingListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Service logic tests (direct calls, no network). IDs match the 7.1 test plan. */
class GradeServiceImplTest {

    private GradeServiceImpl svc;

    @BeforeEach
    void setUp() throws Exception {
        svc = TestSupport.service();
    }

    @AfterEach
    void tearDown() {
        svc.close();
    }

    private Notifier.PublishReport awaitPublish() throws Exception {
        return svc.lastPublication().get(5, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("TC01 normal use: submit 78, publish, getMark returns 78")
    void normalUse() throws Exception {
        svc.submitMark("KYU001", "SPC2401", 78);
        svc.publishMarks("SPC2401");
        assertEquals(78, svc.getMark("KYU001", "SPC2401"));
    }

    @Test
    @DisplayName("TC02 getMark before publish is refused")
    void notPublished() throws Exception {
        svc.submitMark("KYU001", "SPC2401", 78);
        assertThrows(MarkNotPublishedException.class, () -> svc.getMark("KYU001", "SPC2401"));
    }

    @Test
    @DisplayName("TC03 invalid mark 150 is rejected and nothing is stored")
    void invalidMark150() {
        assertThrows(InvalidMarkException.class, () -> svc.submitMark("KYU001", "SPC2401", 150));
        assertEquals(0, svc.store().size());
    }

    @Test
    @DisplayName("TC04 boundaries: -1 and 101 rejected, 0 and 100 accepted")
    void boundaries() throws Exception {
        assertThrows(InvalidMarkException.class, () -> svc.submitMark("KYU001", "SPC2401", -1));
        assertThrows(InvalidMarkException.class, () -> svc.submitMark("KYU001", "SPC2401", 101));
        svc.submitMark("KYU001", "SPC2401", 0);
        svc.submitMark("KYU002", "SPC2401", 100);
        assertEquals(2, svc.store().size());
    }

    @Test
    @DisplayName("TC05 unknown student gives UnknownStudentException")
    void unknownStudent() throws Exception {
        svc.submitMark("KYU001", "SPC2401", 78);
        svc.publishMarks("SPC2401");
        assertThrows(UnknownStudentException.class, () -> svc.getMark("KYU999", "SPC2401"));
    }

    @Test
    @DisplayName("TC06 blank or malformed student ID is rejected")
    void badStudentId() {
        assertThrows(InvalidMarkException.class, () -> svc.submitMark("", "SPC2401", 50));
        assertThrows(InvalidMarkException.class, () -> svc.submitMark(null, "SPC2401", 50));
        assertThrows(InvalidMarkException.class, () -> svc.submitMark("KYU|001", "SPC2401", 50));
    }

    @Test
    @DisplayName("TC07 retried submitMark with the same request ID is applied once")
    void duplicateRequestAppliedOnce() throws Exception {
        svc.submitMark("req-1", "KYU001", "SPC2401", 78);
        svc.submitMark("req-1", "KYU001", "SPC2401", 78);   // retry after a lost reply
        assertEquals(1, svc.submittedCount());
        // A late duplicate must not overwrite a newer correction:
        svc.submitMark("req-2", "KYU001", "SPC2401", 80);
        svc.submitMark("req-1", "KYU001", "SPC2401", 78);
        assertEquals(80, svc.store().get("KYU001", "SPC2401").getAsInt());
        assertEquals(2, svc.submittedCount());
    }

    @Test
    @DisplayName("TC08 a correction with a new request ID replaces the mark")
    void correction() throws Exception {
        svc.submitMark("KYU001", "SPC2401", 60);
        svc.submitMark("KYU001", "SPC2401", 65);
        svc.publishMarks("SPC2401");
        assertEquals(65, svc.getMark("KYU001", "SPC2401"));
    }

    @Test
    @DisplayName("TC09 publishing a unit with no marks is refused")
    void publishUnknownUnit() {
        assertThrows(UnknownUnitException.class, () -> svc.publishMarks("SPC9999"));
    }

    @Test
    @DisplayName("TC10 publish notifies only subscribed students who have a mark in the unit")
    void publishNotifiesRightStudents() throws Exception {
        RecordingListener a = new RecordingListener();
        RecordingListener b = new RecordingListener();
        RecordingListener other = new RecordingListener();
        svc.subscribe("KYU001", a);
        svc.subscribe("KYU002", b);
        svc.subscribe("KYU777", other);                 // no mark in SPC2401
        svc.submitMark("KYU001", "SPC2401", 78);
        svc.submitMark("KYU002", "SPC2401", 64);
        svc.publishMarks("SPC2401");
        Notifier.PublishReport r = awaitPublish();
        assertEquals(2, r.delivered());
        assertEquals(List.of("SPC2401"), a.units);
        assertEquals(List.of("SPC2401"), b.units);
        assertTrue(other.units.isEmpty());
    }

    @Test
    @DisplayName("TC11 an unreachable listener is skipped and removed; the others are still told")
    void deadListenerRemoved() throws Exception {
        RecordingListener alive = new RecordingListener();
        svc.subscribe("KYU001", new DeadListener());
        svc.subscribe("KYU002", alive);
        svc.submitMark("KYU001", "SPC2401", 50);
        svc.submitMark("KYU002", "SPC2401", 70);
        svc.publishMarks("SPC2401");
        Notifier.PublishReport r = awaitPublish();
        assertEquals(1, r.delivered());
        assertEquals(List.of("KYU001"), r.removed());
        assertEquals(List.of("SPC2401"), alive.units);
        assertFalse(svc.subscriptions().isSubscribed("KYU001"));
    }

    @Test
    @DisplayName("TC12 an unsubscribed student is not notified")
    void unsubscribe() throws Exception {
        RecordingListener a = new RecordingListener();
        svc.subscribe("KYU001", a);
        svc.unsubscribe("KYU001");
        svc.unsubscribe("KYU001");                       // idempotent
        svc.submitMark("KYU001", "SPC2401", 78);
        svc.publishMarks("SPC2401");
        assertEquals(0, awaitPublish().targets());
        assertTrue(a.units.isEmpty());
    }

    @Test
    @DisplayName("TC13 repeating publish with no change sends nothing; after a correction it notifies again")
    void republish() throws Exception {
        RecordingListener a = new RecordingListener();
        svc.subscribe("KYU001", a);
        svc.submitMark("KYU001", "SPC2401", 78);
        svc.publishMarks("SPC2401");
        awaitPublish();
        svc.publishMarks("SPC2401");                     // lecturer retried the call
        awaitPublish();
        assertEquals(1, a.units.size());
        svc.submitMark("KYU001", "SPC2401", 79);
        svc.publishMarks("SPC2401");
        awaitPublish();
        assertEquals(2, a.units.size());
    }

    @Test
    @DisplayName("TC14 an expired lease is reaped and that student is not notified")
    void leaseExpiry() throws Exception {
        ManualClock clock = new ManualClock();
        try (GradeServiceImpl s = new GradeServiceImpl(TestSupport.settings(), EventLog.silent("t"), clock::get)) {
            RecordingListener sleeper = new RecordingListener();
            RecordingListener awake = new RecordingListener();
            s.subscribe("KYU001", sleeper, 60_000);
            s.subscribe("KYU002", awake, 60_000);
            s.submitMark("KYU001", "SPC2401", 50);
            s.submitMark("KYU002", "SPC2401", 60);
            // KYU001's laptop sleeps for 2 days; KYU002's stays awake and renews every 30 s.
            long twoDays = 2L * 24 * 60 * 60 * 1000;
            for (long t = 0; t < twoDays; t += 30_000) {
                clock.advance(30_000);
                s.renewLease("KYU002");
            }
            s.housekeep();
            assertFalse(s.subscriptions().isSubscribed("KYU001"));
            s.publishMarks("SPC2401");
            s.lastPublication().get(5, TimeUnit.SECONDS);
            assertTrue(sleeper.units.isEmpty());
            assertEquals(List.of("SPC2401"), awake.units);
        }
    }

    @Test
    @DisplayName("TC15 renewing after expiry is refused so the client knows to re-subscribe")
    void renewAfterExpiry() throws Exception {
        ManualClock clock = new ManualClock();
        try (GradeServiceImpl s = new GradeServiceImpl(TestSupport.settings(), EventLog.silent("t"), clock::get)) {
            s.subscribe("KYU001", new RecordingListener(), 1_000);
            assertEquals(1_000, s.renewLease("KYU001").getDurationMillis());
            clock.advance(1_001);
            assertThrows(NotSubscribedException.class, () -> s.renewLease("KYU001"));
        }
    }

    @Test
    @DisplayName("TC16 requested lease is capped at lease.max.ms")
    void leaseCap() throws Exception {
        assertEquals(120_000, svc.subscribe("KYU001", new RecordingListener(), 10_000_000).getDurationMillis());
        assertEquals(60_000, svc.subscribe("KYU002", new RecordingListener(), 0).getDurationMillis());
    }

    @Test
    @DisplayName("TC17 after a restart the journal restores marks, publication and request IDs")
    void journalRecovery(@TempDir Path dir) throws Exception {
        ServerSettings st = TestSupport.settings();
        st.journalFile = dir.resolve("marks.journal").toString();
        try (GradeServiceImpl first = new GradeServiceImpl(st, EventLog.silent("t"))) {
            first.submitMark("req-A", "KYU001", "SPC2401", 78);
            first.submitMark("req-B", "KYU002", "SPC2401", 55);
            first.publishMarks("SPC2401");
        }
        try (GradeServiceImpl second = new GradeServiceImpl(st, EventLog.silent("t"))) {
            assertEquals(78, second.getMark("KYU001", "SPC2401"));
            assertEquals(55, second.getMark("KYU002", "SPC2401"));
            second.submitMark("req-A", "KYU001", "SPC2401", 78);   // retry that arrives after restart
            assertEquals(2, second.submittedCount());               // not applied again
        }
    }
}
