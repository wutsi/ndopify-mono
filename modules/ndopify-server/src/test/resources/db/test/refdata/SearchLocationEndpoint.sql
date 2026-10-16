INSERT INTO T_LOCATION (id, parent_fk, type, country, name, ascii_name)
    VALUES (237,     null,  'COUNTRY', 'CM', 'Cameroon', 'Cameroon'),
           (23701,   237,   'STATE', 'CM', 'Centre', 'Centre'),
           (23702,   237,   'CITY', 'CM', 'Ouest', 'Ouest'),
           (2370201, 23702, 'CITY', 'CM', 'Bafoussam', 'bafoussam'),
           (2370202, 23702, 'CITY', 'CM', 'Bafang', 'bafang'),
           (2370103, 23702, 'CITY', 'CM', 'Bafou', 'Bafou'),
           (2370104, 23702, 'CITY', 'CM', 'Bana', 'Bana'),
           (2370101, 23701, 'CITY', 'CM', 'Yaounde', 'yaounde'),
           (2370102, 23701, 'CITY', 'CM', 'Obala', 'obala'),
           (1,       null,  'COUNTRY', 'CA', 'Canada', 'canada'),
           (1514,    1,     'STATE', 'CA', 'Quebec', 'quebec'),
           (151401,  1515,  'CITY', 'CA', 'Montreal', 'montreal')
;
