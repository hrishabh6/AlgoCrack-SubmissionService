-- Durable work queue for question statistics.
-- Written in the same transaction as the submission verdict so a crash cannot
-- drop the statistics update, and applied in a separate transaction so a
-- version conflict cannot roll the verdict back.

CREATE TABLE IF NOT EXISTS `question_statistics_outbox` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `submission_id` varchar(36) NOT NULL,
  `question_id` bigint NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `attempts` int NOT NULL DEFAULT 0,
  `next_attempt_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_error` varchar(512) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_stats_outbox_submission` (`submission_id`),
  KEY `idx_stats_outbox_ready` (`status`, `next_attempt_at`),
  KEY `idx_stats_outbox_question` (`question_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
