-- Remember which Semantic Metadata V1 profile a complexity analysis used.
-- Benchmark profile columns stay independent. Existing rows keep a null semantic profile.

ALTER TABLE complexity_analysis
  ADD COLUMN semantic_profile_id varchar(64) DEFAULT NULL AFTER profile_hash,
  ADD COLUMN semantic_profile_version varchar(32) DEFAULT NULL AFTER semantic_profile_id,
  ADD COLUMN semantic_profile_hash char(64) DEFAULT NULL AFTER semantic_profile_version,
  ADD COLUMN semantic_profile_state varchar(32) DEFAULT NULL AFTER semantic_profile_hash,
  ADD COLUMN semantic_profile_json json DEFAULT NULL AFTER semantic_profile_state;
