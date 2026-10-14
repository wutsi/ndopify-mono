CREATE TABLE T_MANDATE(
  id                      BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id               BIGINT NOT NULL DEFAULT 1,

  customer_id             BIGINT NOT NULL,
  agent_id                BIGINT,
  city_id                 BIGINT NOT NULL,

  status                  INT NOT NULL DEFAULT 0,
  mandate_type            INT NOT NULL DEFAULT 0,
  listing_type            INT NOT NULL DEFAULT 0,

  property_category       INT NOT NULL DEFAULT 0,
  property_type           INT,
  furniture_type          INT,
  rooms                   VARCHAR(10),
  parkings                VARCHAR(10),
  area                    INT,

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
  price_periodicity_type  INT NOT NULL DEFAULT 0,
  currency_code           VARCHAR(3) NOT NULL DEFAULT '',
  security_deposit        INT,
  advance_rent            INT,
  lease_term              INT,
  notice_period           INT,
  moving_period           INT NOT NULL DEFAULT 0,

  preferred_contact_period INT,
  comments                TEXT,
  created_at              DATETIME DEFAULT NOW(),
  modified_at             DATETIME NOT NULL DEFAULT now() ON UPDATE now(),


  CONSTRAINT FK_MANDATE_customer FOREIGN KEY(customer_id) REFERENCES T_CUSTOMER(id),
  CONSTRAINT FK_MANDATE_agent FOREIGN KEY(agent_id) REFERENCES T_AGENT(id),
  PRIMARY KEY(id)
) ENGINE = InnoDB;

CREATE INDEX I_MANDATE_tenant ON T_MANDATE(tenant_id);
CREATE INDEX I_MANDATE_status ON T_MANDATE(status);
CREATE INDEX I_MANDATE_city ON T_MANDATE(city_id);


CREATE TABLE T_MANDATE_NEIGHBORHOOD(
  mandate_id              BIGINT NOT NULL,
  neighborhood_id         BIGINT NOT NULL,


  CONSTRAINT FK_MANDATE_NEIGHBORHOOD_mandate FOREIGN KEY(mandate_id) REFERENCES T_MANDATE(id) ON DELETE CASCADE,
  PRIMARY KEY(mandate_id, neighborhood_id)
) ENGINE = InnoDB;
