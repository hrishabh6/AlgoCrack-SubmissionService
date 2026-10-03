-- V3__playground.sql — saved playground workspaces (Submission Service only)

CREATE TABLE IF NOT EXISTS `playground` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` varchar(255) NOT NULL,
  `title` varchar(120) NOT NULL,
  `language` varchar(20) NOT NULL,
  `source_code` mediumtext NOT NULL,
  `stdin` text NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_playground_user_recent` (`user_id`, `updated_at`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
