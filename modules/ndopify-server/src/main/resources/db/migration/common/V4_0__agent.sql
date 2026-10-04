CREATE TABLE T_AGENT(
  id                      BIGINT NOT NULL AUTO_INCREMENT,

  tenant_id               BIGINT NOT NULL,
  user_id                 BIGINT NOT NULL,

  first_name              VARCHAR(100) NOT NULL,
  last_name               VARCHAR(100) NOT NULL,
  whatsapp_number         VARCHAR(20),
  agent_type              INT NOT NULL DEFAULT 0,
  experience_level        INT NOT NULL DEFAULT 0,
  city_id                 BIGINT,
  mobile_money_number     VARCHAR(20),
  mobile_money_gateway    INT NOT NULL DEFAULT 0,

  biography               TEXT,
  agency_name             VARCHAR(100),
  agency_logo_url         TEXT,
  photo_url               TEXT,

  mobile_money_kyc_status INT  NOT NULL DEFAULT 0,
  identity_kyc_status     INT  NOT NULL DEFAULT 0,
  status                  INT  NOT NULL DEFAULT 0,

  created_at              DATETIME DEFAULT NOW(),
  modified_at             DATETIME NOT NULL DEFAULT now() ON UPDATE now(),

  UNIQUE (tenant_id, user_id),
  PRIMARY KEY(id)
) ENGINE = InnoDB;

CREATE INDEX I_AGENT_tenant ON T_AGENT(tenant_id);
CREATE INDEX I_AGENT_user ON T_AGENT(user_id);
CREATE INDEX I_AGENT_city ON T_AGENT(city_id);
CREATE INDEX I_AGENT_mobile_money_number ON T_AGENT(mobile_money_number);
CREATE INDEX I_AGENT_whatsapp_number ON T_AGENT(whatsapp_number);


CREATE TABLE T_AGENT_NEIGHBORHOOD(
  agent_id                BIGINT NOT NULL REFERENCES T_AGENT(id) ON DELETE CASCADE,
  neighborhood_id         BIGINT NOT NULL,

  PRIMARY KEY(agent_id, neighborhood_id)
) ENGINE = InnoDB;
