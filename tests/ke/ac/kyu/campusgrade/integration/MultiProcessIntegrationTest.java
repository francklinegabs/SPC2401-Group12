package ke.ac.kyu.campusgrade.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 7.2 integration test: the server and two student clients run as SEPARATE
 * JVM processes; a lecturer process submits and publishes; both students must
 * receive the notification and read their own mark.
 * Outputs are kept in evidence/logs/integration/ as evidence.
 */
class MultiProcessIntegrationTest {

    private static final String SERVER = "ke.ac.kyu.campusgrade.server.GradeServer";
    private static final String STUDENT = "ke.ac.kyu.campusgrade.client.StudentClient";
    private static final String LECTURER = "ke.ac.kyu.campusgrade.client.LecturerClient";

    @Test
    @DisplayName("IT01 server + 2 student processes + lecturer process: both students are notified")
    void notificationReachesTwoProcesses() throws Exception {
        int port = RmiIntegrationTest.freePort();
        Path dir = Path.of("evidence", "logs", "integration");
        List<String> common = List.of(
                "-Dkyu.registry.host=localhost",
                "-Dkyu.registry.port=" + port,
                "-Dkyu.log.dir=" + dir.resolve("logs"),
                "-Dkyu.server.journal=none");

        try (Proc server = Proc.start("server", dir, plus(common, "-Dkyu.registry.create=true"), SERVER)) {
            assertTrue(server.awaitOutput("server ready", 60_000), server.output());

            try (Proc s1 = Proc.start("student-KYU001", dir, common, STUDENT, "KYU001", "--exit-after", "1");
                 Proc s2 = Proc.start("student-KYU002", dir, common, STUDENT, "KYU002", "--exit-after", "1")) {
                assertTrue(s1.awaitOutput("SUBSCRIBED", 60_000), s1.output());
                assertTrue(s2.awaitOutput("SUBSCRIBED", 60_000), s2.output());

                try (Proc lecturer = Proc.start("lecturer", dir, common, LECTURER,
                        "submit KYU001 SPC2401 78; submit KYU002 SPC2401 64; submit KYU003 SPC2401 150;"
                                + " publish SPC2401; list SPC2401")) {
                    assertTrue(lecturer.awaitExit(60_000), "lecturer did not finish");
                    String lo = lecturer.output();
                    assertTrue(lo.contains("OK published SPC2401"), lo);
                    assertTrue(lo.contains("REFUSED InvalidMarkException: Mark 150"), lo);
                }

                assertTrue(s1.awaitExit(60_000), "KYU001 was never notified:\n" + s1.output());
                assertTrue(s2.awaitExit(60_000), "KYU002 was never notified:\n" + s2.output());
                assertEquals(0, s1.exitCode());
                assertEquals(0, s2.exitCode());
                assertTrue(s1.output().contains("NOTIFIED marks published for SPC2401"));
                assertTrue(s1.output().contains("MARK KYU001 SPC2401 = 78"));
                assertTrue(s2.output().contains("NOTIFIED marks published for SPC2401"));
                assertTrue(s2.output().contains("MARK KYU002 SPC2401 = 64"));
            }
            assertTrue(server.awaitOutput("delivered 2/2", 10_000), server.output());
            System.out.println("[IT01] server, 2 students and lecturer ran as 4 processes; both students notified."
                    + " Outputs in " + dir.toAbsolutePath());
        }
    }

    static List<String> plus(List<String> base, String... more) {
        List<String> l = new java.util.ArrayList<>(base);
        l.addAll(List.of(more));
        return l;
    }
}
