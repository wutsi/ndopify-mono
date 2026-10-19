INSERT INTO T_TENANT(id, name, domain_name, locales, country_code, logo_url, partner_central_url, e_signature_portal_url) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM', 'https://cdn.ndopify.com/logo.png', 'https://partner.ndopify.com', 'https://sign.ndopify.com'),
    (2, 'Other', 'other.localhost', 'fr-CM, en-CM', 'CM', null, null, null);

INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible'),
    (101, 1, 'john.smith@gmail.com', 'John', 'Smith'),
    (900, 2, 'other.tenant@example.com', 'Other', 'Tenant');

-- c-1: draft; c-2: already sent for signature, signed by one of its parties, past its deadline but not expired yet;
-- c-3: draft, with a party that doesn't sign it (an agent); c-4: expired; c-5: cancelled; c-6: signed; c-9: other tenant
INSERT INTO T_CONTRACT(id, tenant_id, number, type, status, content_url, duration_months, signature_deadline, valid_from, valid_until, created_at, signed_at) VALUES
    ('c-1', 1, 'MNDT-2024-000001', 'SEARCH', 'DRAFT', 'https://cdn.ndopify.com/contracts/c-1.pdf', 6, null, null, null, '2024-05-20 00:00:00', null),
    ('c-2', 1, 'MNDT-2024-000002', 'SEARCH', 'PENDING_SIGNATURE', 'https://cdn.ndopify.com/contracts/c-2.pdf', 6, '2024-05-27 00:00:00', null, null, '2024-05-20 00:00:00', null),
    ('c-3', 1, 'MNDT-2024-000003', 'SEARCH', 'DRAFT', 'https://cdn.ndopify.com/contracts/c-3.pdf', 6, null, null, null, '2024-05-20 00:00:00', null),
    ('c-4', 1, 'MNDT-2024-000004', 'SEARCH', 'EXPIRED', 'https://cdn.ndopify.com/contracts/c-4.pdf', 6, '2020-06-01 00:00:00', null, null, '2024-05-20 00:00:00', null),
    ('c-5', 1, 'MNDT-2024-000005', 'SEARCH', 'CANCELLED', 'https://cdn.ndopify.com/contracts/c-5.pdf', 6, null, null, null, '2024-05-20 00:00:00', null),
    ('c-6', 1, 'MNDT-2024-000006', 'SEARCH', 'SIGNED', 'https://cdn.ndopify.com/contracts/c-6.pdf', 6, '2024-05-27 00:00:00', '2024-05-21 00:00:00', '2024-11-21 00:00:00', '2024-05-20 00:00:00', '2024-05-21 00:00:00'),
    ('c-9', 2, 'MNDT-2024-000001', 'SEARCH', 'DRAFT', 'https://cdn.ndopify.com/contracts/c-9.pdf', 6, null, null, null, '2024-05-20 00:00:00', null);

INSERT INTO T_CONTRACT_PARTY(id, tenant_id, contract_id, party_id, role, legal_name, legal_email, signed, ip_address, user_agent, signed_at) VALUES
    ('cp-11', 1, 'c-1', 100, 'MANDATOR', 'Ray Sponsible', 'ray.sponsible@gmail.com', false, null, null, null),
    ('cp-12', 1, 'c-1', 101, 'GUARANTOR', 'John Smith', 'john.smith@gmail.com', false, null, null, null),
    ('cp-21', 1, 'c-2', 100, 'MANDATOR', 'Ray Sponsible', 'ray.sponsible@gmail.com', false, null, null, null),
    ('cp-22', 1, 'c-2', 101, 'GUARANTOR', 'John Smith', 'john.smith@gmail.com', true, '10.0.0.1', 'Mozilla/5.0', '2024-05-21 00:00:00'),
    ('cp-41', 1, 'c-4', 100, 'MANDATOR', 'Ray Sponsible', 'ray.sponsible@gmail.com', false, null, null, null),
    ('cp-51', 1, 'c-5', 100, 'MANDATOR', 'Ray Sponsible', 'ray.sponsible@gmail.com', false, null, null, null),
    ('cp-61', 1, 'c-6', 100, 'MANDATOR', 'Ray Sponsible', 'ray.sponsible@gmail.com', true, '10.0.0.1', 'Mozilla/5.0', '2024-05-21 00:00:00'),
    ('cp-91', 2, 'c-9', 900, 'MANDATOR', 'Other Tenant', 'other.tenant@example.com', false, null, null, null);

INSERT INTO T_CONTRACT_PARTY(id, tenant_id, contract_id, party_id, role, legal_name, legal_email, signature_required) VALUES
    ('cp-31', 1, 'c-3', 100, 'MANDATOR', 'Ray Sponsible', 'ray.sponsible@gmail.com', true),
    ('cp-32', 1, 'c-3', 101, 'AGENT', 'John Smith', 'john.smith@gmail.com', false);
