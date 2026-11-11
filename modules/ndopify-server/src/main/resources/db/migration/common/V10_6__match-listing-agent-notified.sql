ALTER TABLE T_MATCH
    ADD COLUMN listing_agent_notified_at DATETIME DEFAULT NULL;

-- Rows that predate this slice: keep them visible, but do not e-mail a one-time backlog
UPDATE T_MATCH
    SET notified_at = COALESCE(created_at, NOW())
    WHERE co_brokerage = TRUE AND notified_at IS NULL;

UPDATE T_MATCH
    SET listing_agent_notified_at = modified_at
    WHERE co_brokerage = TRUE AND listing_agent_notified_at IS NULL
      AND status IN ('SHORTLISTED', 'PROPOSED', 'ACCEPTED', 'EXPIRED');
