-- The mandates whose ending makes the fixture visits moot:
--   search side: 1 (agent 20) and 4 (agent 30)   listing side: 11 (listing 101)
UPDATE T_MANDATE SET status = 'CANCELLED' WHERE id IN (1, 4);
UPDATE T_MANDATE SET status = 'CLOSED' WHERE id = 11;
