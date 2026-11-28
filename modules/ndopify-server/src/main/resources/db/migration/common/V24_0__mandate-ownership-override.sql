-- #377: the admin's audited override of a LISTING mandate's owner verdict. The three fields are set and cleared
-- together; a NULL reason means no override. No history table: the set/clear log line is the trace of the action.
ALTER TABLE T_MANDATE
    ADD COLUMN ownership_override_reason VARCHAR(500) NULL,
    ADD COLUMN ownership_override_by BIGINT NULL,
    ADD COLUMN ownership_override_at DATETIME NULL;
