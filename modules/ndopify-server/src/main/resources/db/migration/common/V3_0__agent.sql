CREATE TABLE T_AGENT(
  id                     BIGINT NOT NULL AUTO_INCREMENT,

  tenant_id              BIGINT NOT NULL,
  user_id                BIGINT,

  agent_type             INT NOT NULL DEFAULT 0,
  first_name             VARCHAR(100) NOT NULL,
  last_name              VARCHAR(100) NOT NULL,
  biography              TEXT,
  agency_name            VARCHAR(100),
  city_id                BIGINT,
  neighborhood_ids       TEXT,
  agency_logo_url        TEXT,
  photo_url              TEXT,

  mobile_money_number    VARCHAR(20),
  mobile_money_gateway   INT NOT NULL DEFAULT 0,
  mobile_money_kyc_status INT  NOT NULL DEFAULT 0,

  identity_kyc_status    INT  NOT NULL DEFAULT 0,
  status                 INT  NOT NULL DEFAULT 0,

  created_at             DATETIME DEFAULT NOW(),
  modified_at            DATETIME NOT NULL DEFAULT now() ON UPDATE now(),

  PRIMARY KEY(id)
) ENGINE = InnoDB;

CREATE INDEX I_AGENT_tenant ON T_AGENT(tenant_id);
CREATE INDEX I_AGENT_user ON T_AGENT(user_id);
CREATE INDEX I_AGENT_city ON T_AGENT(city_id);
CREATE INDEX I_AGENT_mobile_money_number ON T_AGENT(mobile_money_number);
