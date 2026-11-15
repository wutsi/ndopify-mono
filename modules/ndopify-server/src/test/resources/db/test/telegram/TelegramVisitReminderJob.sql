-- The agent of visit 1 (RemindVisitsJob.sql) is linked to the bot; tenant 2's agent 90 stays unlinked
INSERT INTO T_AGENT_TELEGRAM(tenant_id, agent_id, chat_id, linked_at) VALUES
    (1, 20, 1000, '2026-11-15 00:00:00');
