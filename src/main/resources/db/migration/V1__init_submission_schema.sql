-- V1__init_submission_schema.sql
-- SubmissionService schema — owns submission, execution_metrics, question_statistics
-- Generated from: mysqldump --no-data leetcode (2026-03-05)
-- Note: Cross-service FKs to user(id) and question(id) REMOVED.
--       user_id and question_id kept as regular columns (no FK constraint).

-- submission (FKs to user and question REMOVED — those live in other databases)
CREATE TABLE IF NOT EXISTS `submission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` timestamp NOT NULL,
  `updated_at` timestamp NOT NULL,
  `user_id` varchar(255) NOT NULL,
  `question_id` bigint NOT NULL,
  `code` text,
  `language` varchar(20) NOT NULL,
  `is_passed` tinyint(1) DEFAULT NULL,
  `time_of_submission` timestamp NULL DEFAULT NULL,
  `type_of_error` varchar(255) DEFAULT NULL,
  `compilation_output` text,
  `completed_at` datetime(6) DEFAULT NULL,
  `error_message` text,
  `ip_address` varchar(45) DEFAULT NULL,
  `memory_kb` int DEFAULT NULL,
  `queued_at` datetime(6) NOT NULL,
  `runtime_ms` int DEFAULT NULL,
  `started_at` datetime(6) DEFAULT NULL,
  `status` enum('CANCELLED','COMPILING','COMPLETED','FAILED','QUEUED','RUNNING') NOT NULL,
  `submission_id` varchar(36) NOT NULL,
  `test_results` json DEFAULT NULL,
  `user_agent` text,
  `verdict` enum('ACCEPTED','COMPILATION_ERROR','INTERNAL_ERROR','MEMORY_LIMIT_EXCEEDED','RUNTIME_ERROR','TIME_LIMIT_EXCEEDED','WRONG_ANSWER') DEFAULT NULL,
  `worker_id` varchar(50) DEFAULT NULL,
  `difficulty_level` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK85yv970xece7t5ncjf86ndwoh` (`submission_id`),
  KEY `idx_submission_id` (`submission_id`),
  KEY `idx_user_status` (`user_id`,`status`),
  KEY `idx_question_status` (`question_id`,`status`),
  KEY `idx_status_queued` (`status`,`queued_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- execution_metrics
CREATE TABLE IF NOT EXISTS `execution_metrics` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `compilation_ms` int DEFAULT NULL,
  `container_id` varchar(64) DEFAULT NULL,
  `cpu_time_ms` int DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `execution_ms` int DEFAULT NULL,
  `execution_node` varchar(100) DEFAULT NULL,
  `peak_memory_kb` int DEFAULT NULL,
  `queue_wait_ms` int DEFAULT NULL,
  `submission_id` varchar(36) NOT NULL,
  `test_case_timings` json DEFAULT NULL,
  `total_ms` int DEFAULT NULL,
  `used_cache` bit(1) DEFAULT NULL,
  `worker_id` varchar(50) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_metrics_submission_id` (`submission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- question_statistics
CREATE TABLE IF NOT EXISTS `question_statistics` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `accepted_submissions` int NOT NULL,
  `avg_attempts_to_solve` double DEFAULT NULL,
  `avg_memory_kb` int DEFAULT NULL,
  `avg_runtime_ms` int DEFAULT NULL,
  `best_memory_kb` int DEFAULT NULL,
  `best_runtime_ms` int DEFAULT NULL,
  `last_submission_at` datetime(6) DEFAULT NULL,
  `question_id` bigint NOT NULL,
  `total_submissions` int NOT NULL,
  `unique_attempts` int DEFAULT NULL,
  `unique_solves` int DEFAULT NULL,
  `version` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_stats_question_id` (`question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
