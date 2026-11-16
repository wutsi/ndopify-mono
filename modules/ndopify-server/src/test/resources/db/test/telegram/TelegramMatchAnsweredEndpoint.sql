-- ProposeMatchEndpoint.sql's match 2: customer 10 answers, agent 20 is told
UPDATE T_TENANT SET partner_central_url = 'https://partner.ndopify.com' WHERE id = 1;
INSERT INTO T_AGENT_TELEGRAM(tenant_id, agent_id, chat_id, linked_at) VALUES
    (1, 20, 1000, '2026-11-15 00:00:00');
