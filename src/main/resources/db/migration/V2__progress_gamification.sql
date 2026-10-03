-- Rank & badge / progress module (submission_db)

CREATE TABLE IF NOT EXISTS `user_problem_solve` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `user_id` varchar(255) NOT NULL,
  `question_id` bigint NOT NULL,
  `first_accepted_submission_id` varchar(64) NOT NULL,
  `first_accepted_at` timestamp NOT NULL,
  `difficulty_snapshot` varchar(20) DEFAULT NULL,
  `question_status_snapshot` varchar(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_problem_solve` (`user_id`, `question_id`),
  KEY `idx_ups_question` (`question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `daily_challenge_completion` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `user_id` varchar(255) NOT NULL,
  `daily_challenge_id` bigint NOT NULL,
  `challenge_date` date NOT NULL,
  `question_id` bigint NOT NULL,
  `accepted_submission_id` varchar(64) NOT NULL,
  `difficulty_snapshot` varchar(20) DEFAULT NULL,
  `completed_at` timestamp NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dcc_user_challenge` (`user_id`, `daily_challenge_id`),
  UNIQUE KEY `uk_dcc_submission` (`accepted_submission_id`),
  KEY `idx_dcc_user_date` (`user_id`, `challenge_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `user_progress` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `user_id` varchar(255) NOT NULL,
  `rank_algorithm_version` varchar(32) NOT NULL,
  `unique_solved` bigint NOT NULL DEFAULT 0,
  `easy_solved` bigint NOT NULL DEFAULT 0,
  `medium_solved` bigint NOT NULL DEFAULT 0,
  `hard_solved` bigint NOT NULL DEFAULT 0,
  `easy_potd_completed` bigint NOT NULL DEFAULT 0,
  `medium_potd_completed` bigint NOT NULL DEFAULT 0,
  `hard_potd_completed` bigint NOT NULL DEFAULT 0,
  `total_potd_completed` bigint NOT NULL DEFAULT 0,
  `current_potd_streak` int NOT NULL DEFAULT 0,
  `longest_potd_streak` int NOT NULL DEFAULT 0,
  `breadth_qualified_topics` int NOT NULL DEFAULT 0,
  `mastery_score` bigint NOT NULL DEFAULT 0,
  `potd_score` bigint NOT NULL DEFAULT 0,
  `breadth_score` bigint NOT NULL DEFAULT 0,
  `quality_score` bigint NOT NULL DEFAULT 0,
  `contest_score` bigint NOT NULL DEFAULT 0,
  `total_rank_score` bigint NOT NULL DEFAULT 0,
  `rank_tier_code` varchar(32) NOT NULL DEFAULT 'NOVICE',
  `source_watermark` varchar(128) DEFAULT NULL,
  `score_achieved_at` timestamp NULL DEFAULT NULL,
  `calculated_at` timestamp NOT NULL,
  `version` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_progress_version` (`user_id`, `rank_algorithm_version`),
  KEY `idx_leaderboard_rank_v1` (
    `rank_algorithm_version`,
    `total_rank_score` DESC,
    `hard_solved` DESC,
    `medium_solved` DESC,
    `total_potd_completed` DESC,
    `score_achieved_at` ASC,
    `user_id` ASC
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `user_topic_progress` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `user_id` varchar(255) NOT NULL,
  `tag_id` bigint NOT NULL,
  `tag_name_snapshot` varchar(255) DEFAULT NULL,
  `normalized_credit` decimal(10,4) NOT NULL DEFAULT 0,
  `contributing_solves` int NOT NULL DEFAULT 0,
  `topic_level` varchar(32) NOT NULL DEFAULT 'UNQUALIFIED',
  `rank_algorithm_version` varchar(32) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_topic_version` (`user_id`, `tag_id`, `rank_algorithm_version`),
  KEY `idx_utp_user_version` (`user_id`, `rank_algorithm_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `badge_definition` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `code` varchar(64) NOT NULL,
  `name` varchar(128) NOT NULL,
  `description` varchar(512) NOT NULL,
  `category` varchar(32) NOT NULL,
  `icon_key` varchar(64) NOT NULL,
  `display_tier` varchar(32) DEFAULT NULL,
  `sort_order` int NOT NULL DEFAULT 0,
  `active` tinyint(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_badge_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `user_badge` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `user_id` varchar(255) NOT NULL,
  `badge_code` varchar(64) NOT NULL,
  `earned_at` timestamp NOT NULL,
  `source_type` varchar(32) NOT NULL,
  `source_reference` varchar(128) DEFAULT NULL,
  `evidence_summary` json DEFAULT NULL,
  `rank_algorithm_version` varchar(32) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_badge` (`user_id`, `badge_code`),
  KEY `idx_user_badge_user` (`user_id`, `earned_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `progress_update_outbox` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `event_type` varchar(32) NOT NULL,
  `source_id` varchar(64) NOT NULL,
  `user_id` varchar(255) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `attempts` int NOT NULL DEFAULT 0,
  `next_attempt_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_error` varchar(512) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_progress_outbox_event` (`event_type`, `source_id`),
  KEY `idx_outbox_pending` (`status`, `next_attempt_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `progress_rebuild_run` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `target_version` varchar(32) NOT NULL,
  `configuration_hash` varchar(64) NOT NULL,
  `status` varchar(20) NOT NULL,
  `cursor_user_id` varchar(255) DEFAULT NULL,
  `processed_users` bigint NOT NULL DEFAULT 0,
  `failed_users` bigint NOT NULL DEFAULT 0,
  `actor` varchar(255) DEFAULT NULL,
  `dry_run` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_rebuild_status_created` (`status`, `created_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `badge_definition` (`code`, `name`, `description`, `category`, `icon_key`, `display_tier`, `sort_order`, `active`) VALUES
('FIRST_SOLVE', 'First Crack', 'Solve your first unique problem', 'PROBLEM', 'sparkles', 'BRONZE', 10, 1),
('SOLVED_10', 'Getting Started', 'Solve 10 unique problems', 'PROBLEM', 'target', 'BRONZE', 20, 1),
('SOLVED_50', 'Problem Solver', 'Solve 50 unique problems', 'PROBLEM', 'trophy', 'SILVER', 30, 1),
('SOLVED_100', 'Century', 'Solve 100 unique problems', 'PROBLEM', 'medal', 'GOLD', 40, 1),
('MEDIUM_10', 'Rising Challenge', 'Solve 10 unique Medium problems', 'DIFFICULTY', 'trending-up', 'BRONZE', 50, 1),
('MEDIUM_25', 'Medium Mastery', 'Solve 25 unique Medium problems', 'DIFFICULTY', 'award', 'SILVER', 60, 1),
('HARD_5', 'Hard Beginnings', 'Solve 5 unique Hard problems', 'DIFFICULTY', 'flame', 'BRONZE', 70, 1),
('HARD_10', 'Hard Hitter', 'Solve 10 unique Hard problems', 'DIFFICULTY', 'zap', 'SILVER', 80, 1),
('HARD_25', 'Hard Specialist', 'Solve 25 unique Hard problems', 'DIFFICULTY', 'crown', 'GOLD', 90, 1),
('POTD_FIRST', 'Daily Debut', 'Complete your first daily challenge', 'POTD', 'calendar', 'BRONZE', 100, 1),
('POTD_7', 'Daily Regular', 'Complete 7 daily challenges', 'POTD', 'calendar-check', 'BRONZE', 110, 1),
('POTD_30', 'Daily Dedicated', 'Complete 30 daily challenges', 'POTD', 'calendar-days', 'SILVER', 120, 1),
('POTD_100', 'Daily Centurion', 'Complete 100 daily challenges', 'POTD', 'calendar-range', 'GOLD', 130, 1),
('POTD_STREAK_7', 'One Week Strong', 'Reach a 7-day longest POTD streak', 'STREAK', 'flame', 'SILVER', 140, 1),
('POTD_STREAK_30', 'Monthly Momentum', 'Reach a 30-day longest POTD streak', 'STREAK', 'flame-kindling', 'GOLD', 150, 1),
('TOPIC_EXPLORER', 'Breadth Explorer', 'Reach Explorer in at least 4 topics', 'TOPIC', 'compass', 'SILVER', 160, 1),
('GRAPH_PRACTITIONER', 'Graph Explorer', 'Earn Practitioner level in Graph', 'TOPIC', 'git-branch', 'SILVER', 170, 1);
