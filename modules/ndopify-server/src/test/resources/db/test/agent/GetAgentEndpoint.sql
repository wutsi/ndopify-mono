INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name, created_at, modified_at) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (900, 2, 'other.tenant@example.com', 'Other', 'Tenant', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_AGENT (
    id, tenant_id, party_id, agent_type, biography,
    city_id, whatsapp_number, experience_level
) VALUES
    (
        1, 1, 100, 1, 'Top agent in town',
        2370201, '+23761111111', 1
    ),
    (
        2, 2, 900, 1, 'Agent in another tenant',
        2370201, '+23762222222', 1
    );

INSERT INTO T_AGENT_NEIGHBORHOOD (agent_id, neighborhood_id) VALUES
    (1, 111),
    (1, 222);
