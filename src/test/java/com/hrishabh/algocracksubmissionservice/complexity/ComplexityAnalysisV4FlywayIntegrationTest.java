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
    void v5ProfileExecutionIdColumnExists() throws SQLException {
        try (Connection connection = connection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT COLUMN_NAME FROM information_schema.COLUMNS "
                             + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'complexity_analysis' "
                             + "AND COLUMN_NAME = 'profile_execution_id'")) {
            assertTrue(rs.next(), "profile_execution_id column missing after V5");
        }
    }

    @Test
    void v6BenchmarkCaseIdentityUniquePerAnalysis() throws SQLException {
        String analysisId = "a-bench-" + System.nanoTime();
        String submissionId = "sub-bench-" + System.nanoTime();
        insertAnalysis(submissionId, analysisId, null, "COMPLETED");
        String insertBench = """
                INSERT INTO complexity_benchmark_run (
                  analysis_id, case_id, case_identity, variant, size_vector_json, input_hash,
                  sample_count, warmup_count, output_validated, outcome, created_at
                ) VALUES (?, 'case-1', 'identity-1', 'default', '{}', 'abc', 1, 0, 1, 'SUCCESS', NOW(6))
                """;
        try (Connection connection = connection(); PreparedStatement ps = connection.prepareStatement(insertBench)) {
            ps.setString(1, analysisId);
            assertEquals(1, ps.executeUpdate());
        }
        String duplicateIdentity = """
                INSERT INTO complexity_benchmark_run (
                  analysis_id, case_id, case_identity, variant, size_vector_json, input_hash,
                  sample_count, warmup_count, output_validated, outcome, created_at
                ) VALUES (?, 'case-2', 'identity-1', 'default', '{}', 'def', 1, 0, 1, 'SUCCESS', NOW(6))
                """;
        try (Connection connection = connection(); PreparedStatement ps = connection.prepareStatement(duplicateIdentity)) {
            ps.setString(1, analysisId);
            assertThrows(SQLException.class, ps::executeUpdate);
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
