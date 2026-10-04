INSERT INTO T_PARTY(id, email, first_name, last_name, created_at, modified_at) VALUES
    (100, 'roger.milla@gmail.com', 'Roger', 'Milla', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (101, '007@gmail.com', 'J', 'Bond', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (102, 'samuel.eto@gmail.com', 'Samuel', 'Eto', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_AGENT(id, party_id) VALUES
    (100, 100),
    (101, 101),
    (102, 102);

INSERT INTO T_AGENT_NEIGHBORHOOD(agent_id, neighborhood_id) VALUES
    (102, 555),
    (102, 666);
