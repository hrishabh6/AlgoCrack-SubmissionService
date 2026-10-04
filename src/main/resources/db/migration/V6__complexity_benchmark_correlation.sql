-- V6: benchmark correlation + idempotent upsert identity (additive).

ALTER TABLE `complexity_benchmark_run`
  ADD COLUMN `case_identity` varchar(128) NOT NULL DEFAULT '' AFTER `case_id`,
  ADD COLUMN `profile_version` varchar(32) DEFAULT NULL AFTER `case_identity`,
  ADD COLUMN `profile_hash` char(64) DEFAULT NULL AFTER `profile_version`,
  ADD COLUMN `generator_version` varchar(32) DEFAULT NULL AFTER `profile_hash`,
  ADD COLUMN `harness_version` varchar(32) DEFAULT NULL AFTER `generator_version`,
  ADD COLUMN `measurement_policy_version` varchar(32) DEFAULT NULL AFTER `harness_version`;

ALTER TABLE `complexity_benchmark_run`
  ADD UNIQUE KEY `uk_complexity_benchmark_analysis_case_identity` (`analysis_id`, `case_identity`);
