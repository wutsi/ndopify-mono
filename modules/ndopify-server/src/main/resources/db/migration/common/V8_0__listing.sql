CREATE TABLE T_LISTING(
  id                      BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id               BIGINT NOT NULL DEFAULT 1,
  version                 BIGINT NOT NULL DEFAULT 0,

  mandate_id              BIGINT NOT NULL,
  agent_id                BIGINT NOT NULL,
  hero_image_id           BIGINT,

  property_category       VARCHAR(30) DEFAULT NULL,
  listing_type            VARCHAR(30) DEFAULT NULL,
  status                  VARCHAR(30) DEFAULT NULL,

  city_id                 BIGINT NOT NULL,
  neighborhood_id         BIGINT,
  landmark                VARCHAR(255),
  access_directions       TEXT,

  longitude               DOUBLE,
  latitude                DOUBLE,

  property_type           VARCHAR(30) DEFAULT NULL,
  furniture_type          VARCHAR(30) DEFAULT NULL,
  rooms                   INT,
  bathrooms               INT,
  living_rooms            INT,
  kitchens                INT,
  parkings                INT,
  area                    INT,
  levels                  INT,
  floor_level             INT,

  water_meter             BOOLEAN NOT NULL DEFAULT false,
  dedicated_water_meter   BOOLEAN NOT NULL DEFAULT false,
  water_tank              BOOLEAN NOT NULL DEFAULT false,
  water_well              BOOLEAN NOT NULL DEFAULT false,

  electricity_meter       BOOLEAN NOT NULL DEFAULT false,
  dedicated_electricity_meter BOOLEAN NOT NULL DEFAULT false,
  generator_set           BOOLEAN NOT NULL DEFAULT false,
  solar_panel             BOOLEAN NOT NULL DEFAULT false,

  road_paved              BOOLEAN NOT NULL DEFAULT false,
  track_accessible_by_car BOOLEAN NOT NULL DEFAULT false,
  track_inaccessible_by_car BOOLEAN NOT NULL DEFAULT false,

  secured_fenced          BOOLEAN NOT NULL DEFAULT false,
  security_guard          BOOLEAN NOT NULL DEFAULT false,
  video_surveillance      BOOLEAN NOT NULL DEFAULT false,

  price                   BIGINT NOT NULL DEFAULT -1,
  currency_code           VARCHAR(3) NOT NULL DEFAULT '',
  price_periodicity_type  VARCHAR(30) DEFAULT NULL,
  security_deposit        INT,
  advance_rent            INT,
  lease_term              INT,
  notice_period           INT,

  available_from          DATE,

  agent_remarks           TEXT,

  title                   VARCHAR(100),
  summary                 VARCHAR(255),
  description             TEXT,
  url                     TEXT,
  error_codes             TEXT,
  error_message           TEXT,
  retries                 INT,

  created_at              DATETIME DEFAULT NOW(),
  modified_at             DATETIME NOT NULL DEFAULT now() ON UPDATE now(),
  published_at            DATETIME,
  verified_at             DATETIME,
  closed_at               DATETIME,

  UNIQUE(mandate_id), -- One listing per mandate
  CONSTRAINT FK_LISTING_tenant FOREIGN KEY(tenant_id) REFERENCES T_TENANT(id),
  CONSTRAINT FK_LISTING_mandate FOREIGN KEY(mandate_id) REFERENCES T_MANDATE(id),
  CONSTRAINT FK_LISTING_agent FOREIGN KEY(agent_id) REFERENCES T_AGENT(id),
  CONSTRAINT FK_LISTING_city FOREIGN KEY(city_id) REFERENCES T_LOCATION(id),
  CONSTRAINT FK_LISTING_neighborhood FOREIGN KEY(neighborhood_id) REFERENCES T_LOCATION(id),
  PRIMARY KEY(id)
) ENGINE = InnoDB;

CREATE INDEX I_LISTING_tenant ON T_LISTING(tenant_id);
CREATE INDEX I_LISTING_status ON T_LISTING(status);
CREATE INDEX I_LISTING_city ON T_LISTING(city_id);
