INSERT INTO T_LOCATION (id, parent_fk, type, country, name, ascii_name)
    VALUES (237,     null,  1, 'CM', 'Cameroon', 'Cameroon'),
           (23701,   237,   2, 'CM', 'Centre', 'Centre'),
           (23702,   237,   3, 'CM', 'Ouest', 'Ouest'),
           (2370201, 23702, 3, 'CM', 'Bafoussam', 'bafoussam'),
           (2370202, 23702, 3, 'CM', 'Bafang', 'bafang'),
           (2370103, 23702, 3, 'CM', 'Bafou', 'Bafou'),
           (2370104, 23702, 3, 'CM', 'Bana', 'Bana'),
           (2370101, 23701, 3, 'CM', 'Yaounde', 'yaounde'),
           (2370102, 23701, 3, 'CM', 'Obala', 'obala'),
           (1,       null,  1, 'CA', 'Canada', 'canada'),
           (1514,    1,     2, 'CA', 'Quebec', 'quebec'),
           (151401,  1515,  3, 'CA', 'Montreal', 'montreal')
;
