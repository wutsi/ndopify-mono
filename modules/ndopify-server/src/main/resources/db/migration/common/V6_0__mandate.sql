CREATE TABLE T_MANDATE(
  id                      BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id               BIGINT NOT NULL DEFAULT 1,

  customer_id             BIGINT NOT NULL REFERENCES T_CUSTOMER(id),
  agent_id                BIGINT REFERENCES T_AGENT(id),

  status                  INT NOT NULL DEFAULT 0,
  mandate_type            INT NOT NULL DEFAULT 0,
  listing_type            INT NOT NULL DEFAULT 0,

  city_id                 BIGINT NOT NULL,
  property_types          TEXT,
  furniture_type          INT,
  rooms                   VARCHAR(10),
  parkings                VARCHAR(10),
  min_area                INT,
  max_area                INT,

  water_access_type       INT,
  water_tank              BOOLEAN NOT NULL DEFAULT false,
  water_well              BOOLEAN NOT NULL DEFAULT false,

  electricity_access_type INT,
  generator_set           BOOLEAN NOT NULL DEFAULT false,
  solar_panel             BOOLEAN NOT NULL DEFAULT false,

  road_access_type        INT,

  secured_fenced          BOOLEAN NOT NULL DEFAULT false,
  security_guard          BOOLEAN NOT NULL DEFAULT false,
  video_surveillance      BOOLEAN NOT NULL DEFAULT false,

  price                   BIGINT NOT NULL DEFAULT -1,
  price_periodicity_type  INT NOT NULL DEFAULT 0,
  currency_code           VARCHAR(3) NOT NULL DEFAULT '',
  security_deposit        INT,
  advance_rent            INT,
  lease_term              INT,
  notice_period           INT,

  preferred_contact_period INT,
  comments                TEXT,
  created_at              DATETIME DEFAULT NOW(),
  modified_at             DATETIME NOT NULL DEFAULT now() ON UPDATE now(),

  PRIMARY KEY(id)
) ENGINE = InnoDB;

CREATE INDEX I_MANDATE_tenant ON T_MANDATE(tenant_id);
CREATE INDEX I_MANDATE_status ON T_MANDATE(status);
CREATE INDEX I_MANDATE_city ON T_MANDATE(city_id);


CREATE TABLE T_MANDATE_NEIGHBORHOOD(
  mandate_id              BIGINT NOT NULL REFERENCES T_MANDATE(id) ON DELETE CASCADE,
  neighborhood_id         BIGINT NOT NULL,

  PRIMARY KEY(mandate_id, neighborhood_id)
) ENGINE = InnoDB;
