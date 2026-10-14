-- Tenants referenced by the tenant-scoped rows below
INSERT INTO T_TENANT(id, name, domain_name, locales, country_code) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM'),
    (2, 'Other', 'other.localhost', 'fr-CM, en-CM', 'CM');

-- Cities and neighborhoods referenced by the agents and mandates (T_AGENT, T_MANDATE and their neighborhoods)
INSERT INTO T_LOCATION(id, parent_fk, type, country, name, ascii_name) VALUES
    (2370201, NULL, 3, 'CM', 'Yaounde', 'Yaounde'),
    (2220957, NULL, 3, 'CM', 'Douala', 'Douala'),
    (1514, NULL, 3, 'CA', 'Montreal', 'Montreal'),
    (111, 2370201, 4, 'CM', 'Bastos', 'Bastos'),
    (222, 2370201, 4, 'CM', 'Mvan', 'Mvan'),
    (333, 2370201, 4, 'CM', 'Nlongkak', 'Nlongkak'),
    (444, 2370201, 4, 'CM', 'Essos', 'Essos'),
    (555, 2370201, 4, 'CM', 'Biyem-Assi', 'Biyem-Assi'),
    (666, 2370201, 4, 'CM', 'Mendong', 'Mendong');

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
