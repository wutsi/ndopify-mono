INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES
    (100, 1, '007@gmail.com', 'James', 'Bond'),
    (101, 1, 'omam.mbiyick@gmail.com', 'Omam', 'Mbiyick');

INSERT INTO T_CUSTOMER(id, tenant_id, party_id) VALUES
    (101, 1, 101);
