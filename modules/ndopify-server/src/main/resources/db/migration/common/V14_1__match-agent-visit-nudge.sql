ALTER TABLE T_MATCH ADD COLUMN agent_visit_nudged_at DATETIME DEFAULT NULL;
ALTER TABLE T_MATCH ADD INDEX IDX_MATCH_agent_visit_nudge (status, agent_visit_nudged_at);
