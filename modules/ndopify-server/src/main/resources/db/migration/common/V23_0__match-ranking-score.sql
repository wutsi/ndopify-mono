ALTER TABLE T_MATCH
  ADD COLUMN ranking_score DOUBLE NOT NULL DEFAULT 0.0 AFTER score;

-- Rows matched before this story keep today's order until their next re-match: the bonus is never backfilled
UPDATE T_MATCH SET ranking_score = score;
