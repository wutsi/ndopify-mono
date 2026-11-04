-- A cancelled visit of the match the create tests request: it doesn't prevent a new one.
INSERT INTO T_VISIT(
    id, tenant_id, match_id, mandate_id, listing_id, agent_id, customer_id, status, type,
    requested_at, scheduled_at, cancelled_by, cancellation_reason, cancelled_at, created_at
) VALUES
    (1, 1, 1, 1, 101, 20, 10, 'CANCELLED', 'ON_SITE',
     '2026-10-01 10:00:00', '2026-10-02 10:00:00', 'CUSTOMER', 'Empêchement', '2026-10-01 12:00:00', '2026-10-01 10:00:00');
