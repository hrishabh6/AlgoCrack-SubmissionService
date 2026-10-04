package com.hrishabh.algocracksubmissionservice.complexity;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Requires Docker for Testcontainers. Skipped when Docker is unavailable to the test JVM.
 */
class ComplexityAnalysisV4FlywayIntegrationTest {

    private static MySQLContainer<?> mysql;

    @BeforeAll
    static void startDatabaseAndMigrate() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker unavailable — skipping MySQL Flyway integration");
        mysql = new MySQLContainer<>("mysql:8.0")
                .withDatabaseName("algocrack_submission_test")
                .withUsername("test")
                .withPassword("test");
        mysql.start();
        Flyway.configure()
                .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @AfterAll
    static void stopDatabase() {
        if (mysql != null) {
            mysql.stop();
        }
    }

    @Test
    void v4TablesExistAfterMigratingFromV3Head() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            assertTrue(tableExists(statement, "complexity_analysis"));
            assertTrue(tableExists(statement, "complexity_static_finding"));
            assertTrue(tableExists(statement, "complexity_benchmark_run"));
            assertTrue(tableExists(statement, "playground"));
        }
    }

    @Test
    void enforcesOneActiveAnalysisPerSubmission() throws SQLException {
        String submissionId = "sub-active-" + System.nanoTime();
        insertAnalysis(submissionId, "a-active-1", 1, "QUEUED");
        assertThrows(SQLException.class, () -> insertAnalysis(submissionId, "a-active-2", 1, "QUEUED"));
    }

    @Test
    void allowsMultipleTerminalHistoricalAnalyses() throws SQLException {
        String submissionId = "sub-hist-" + System.nanoTime();
        insertAnalysis(submissionId, "a-hist-1", null, "COMPLETED");
        insertAnalysis(submissionId, "a-hist-2", null, "COMPLETED");
        try (Connection connection = connection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT COUNT(*) FROM complexity_analysis WHERE submission_id = ? AND active_slot IS NULL")) {
            ps.setString(1, submissionId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(2, rs.getInt(1));
            }
        }
    }

    @Test
    void terminalRowClearsActiveSlot() throws SQLException {
        String submissionId = "sub-terminal-" + System.nanoTime();
        insertAnalysis(submissionId, "a-term-1", 1, "QUEUED");
        try (Connection connection = connection();
             PreparedStatement ps = connection.prepareStatement(
                     "UPDATE complexity_analysis SET status = ?, active_slot = NULL, completed_at = ? WHERE analysis_id = ?")) {
            ps.setString(1, "COMPLETED");
            ps.setObject(2, LocalDateTime.now());
            ps.setString(3, "a-term-1");
            assertEquals(1, ps.executeUpdate());
        }
        try (Connection connection = connection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT active_slot FROM complexity_analysis WHERE analysis_id = ?")) {
            ps.setString(1, "a-term-1");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertNull(rs.getObject("active_slot"));
            }
        }
        insertAnalysis(submissionId, "a-term-2", 1, "QUEUED");
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
    }

    private static boolean tableExists(Statement statement, String table) throws SQLException {
        try (ResultSet rs = statement.executeQuery("SHOW TABLES LIKE '" + table + "'")) {
            return rs.next();
        }
    }

    private static void insertAnalysis(String submissionId, String analysisId, Integer activeSlot, String status)
            throws SQLException {
        LocalDateTime now = LocalDateTime.now();
        String sql = """
                INSERT INTO complexity_analysis (
                  analysis_id, submission_id, owner_user_id, question_id, language, status,
                  source_sha256, active_slot, requested_at, updated_at, attempt_count, optimistic_version
                ) VALUES (?, ?, 'owner-1', 1, 'JAVA', ?, ?, ?, ?, ?, 0, 0)
                """;
        try (Connection connection = connection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, analysisId);
            ps.setString(2, submissionId);
            ps.setString(3, status);
            ps.setString(4, "abcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcd");
            if (activeSlot == null) {
                ps.setNull(5, java.sql.Types.TINYINT);
            } else {
                ps.setInt(5, activeSlot);
            }
            ps.setObject(6, now);
            ps.setObject(7, now);
            ps.executeUpdate();
        }
    }
}
