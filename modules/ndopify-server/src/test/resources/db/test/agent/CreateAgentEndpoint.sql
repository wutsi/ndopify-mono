INSERT INTO T_PARTY(id, email, first_name, last_name) VALUES
    (100, 'roger.milla@gmail.com', 'Roger', 'Milla'),
    (101, '007@gmail.com', 'James', 'Bond'),
    (102, 'omam.mbiyick@gmail.com', 'Omam', 'Mbiyick');

INSERT INTO T_AGENT(id, party_id) VALUES
    (102, 102);
