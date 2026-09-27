INSERT INTO T_AGENT (
    id, tenant_id, user_id, agent_type, first_name, last_name, biography, agency_name,
    city_id, neighborhood_ids, photo_url, agency_logo_url,
    mobile_money_number, mobile_money_gateway, created_at, modified_at
) VALUES (
    1, 1, 1, 1, 'Ray', 'Sponsible', 'Top agent in town', 'Ray Realty',
    2370201, '111,222', 'https://cdn.example.com/photo.jpg', 'https://cdn.example.com/logo.jpg',
    '+237600000000', 1, NOW(), NOW()
);
