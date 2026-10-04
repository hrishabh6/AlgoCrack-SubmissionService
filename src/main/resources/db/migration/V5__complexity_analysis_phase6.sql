-- V5: Phase 6 orchestration — store CXE profile execution correlation (additive).

ALTER TABLE `complexity_analysis`
  ADD COLUMN `profile_execution_id` varchar(36) DEFAULT NULL AFTER `profiler_runtime_version`;
