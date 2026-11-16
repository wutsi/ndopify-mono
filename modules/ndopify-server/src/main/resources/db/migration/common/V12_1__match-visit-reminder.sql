ALTER TABLE T_MATCH ADD COLUMN visit_reminded_at DATETIME DEFAULT NULL;
ALTER TABLE T_MATCH ADD INDEX IDX_MATCH_visit_reminder (status, visit_reminded_at);
