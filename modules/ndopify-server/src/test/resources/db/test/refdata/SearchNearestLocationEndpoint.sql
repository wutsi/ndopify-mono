-- type: 1=COUNTRY, 3=CITY, 4=NEIGHBORHOOD | distances from Bastos (237002) in km in comments
INSERT INTO T_LOCATION (id, parent_fk, type, country, name, ascii_name, latitude, longitude)
    VALUES (237,     null,    1, 'CM', 'Cameroon',   'Cameroon',   7.36, 12.35),
           (2370101, 237,     3, 'CM', 'Yaounde',    'Yaounde',    3.8667, 11.5167),                       -- 2.95
           (237002,  2370101, 4, 'CM', 'Bastos',     'Bastos',     3.8925339696406587, 11.511400038328873),
           (237034,  2370101, 4, 'CM', 'Ekoudou',    'Ekoudou',    3.8789970169290022, 11.512152516211234), -- 1.51
           (237001,  2370101, 4, 'CM', 'Nlongkak',   'Nlongkak',   3.879225806692496,  11.5203528488888),   -- 1.78
           (237072,  2370101, 4, 'CM', 'Hippodrome', 'Hippodrome', 3.8720832599970314, 11.51542349163051),  -- 2.32
           (237091,  2370101, 4, 'CM', 'Messassi',   'Messassi',   3.9033646491835947, 11.546101818064955), -- 4.03
           (237093,  2370101, 4, 'CM', 'Emana',      'Emana',      3.9216, 11.5375),                       -- 4.34
           (237500,  2370101, 4, 'CM', 'Far Away',   'Far-Away',   4.17, 11.53),                           -- 30.9
           (237501,  2370101, 4, 'CM', 'Near Far',   'Near-Far',   4.18, 11.53),                           -- 32.0, 1.11 from Far Away
           (237999,  2370101, 4, 'CM', 'Nowhere',    'Nowhere',    null, null),
           (1000,    null,    4, 'CA', 'Elsewhere',  'Elsewhere',  3.8925339696406587, 11.511400038328873) -- 0, other country
;
