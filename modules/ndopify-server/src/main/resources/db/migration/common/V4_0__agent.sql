CREATE TABLE T_AGENT(
  id                      BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id               BIGINT NOT NULL DEFAULT 1,

  party_id                BIGINT NOT NULL,

  whatsapp_number         VARCHAR(20),
  agent_type              INT NOT NULL DEFAULT 0,
  experience_level        INT NOT NULL DEFAULT 0,
  city_id                 BIGINT,
  biography               TEXT,
  photo_url               TEXT,
  created_at              DATETIME DEFAULT NOW(),
  modified_at             DATETIME NOT NULL DEFAULT now() ON UPDATE now(),
  joined_at               DATETIME,

  UNIQUE (party_id),

  CONSTRAINT FK_AGENT_party FOREIGN KEY(party_id) REFERENCES T_PARTY(id),
  CONSTRAINT FK_AGENT_tenant FOREIGN KEY(tenant_id) REFERENCES T_TENANT(id),
  CONSTRAINT FK_AGENT_city FOREIGN KEY(city_id) REFERENCES T_LOCATION(id),
  PRIMARY KEY(id)
) ENGINE = InnoDB;

CREATE INDEX I_AGENT_city ON T_AGENT(city_id);
CREATE INDEX I_AGENT_tenant ON T_AGENT(tenant_id);


CREATE TABLE T_AGENT_NEIGHBORHOOD(
  agent_id                BIGINT NOT NULL,
  neighborhood_id         BIGINT NOT NULL,


  CONSTRAINT FK_AGENT_NEIGHBORHOOD_agent FOREIGN KEY(agent_id) REFERENCES T_AGENT(id) ON DELETE CASCADE,
  CONSTRAINT FK_AGENT_NEIGHBORHOOD_neighborhood FOREIGN KEY(neighborhood_id) REFERENCES T_LOCATION(id),
  PRIMARY KEY(agent_id, neighborhood_id)
) ENGINE = InnoDB;
