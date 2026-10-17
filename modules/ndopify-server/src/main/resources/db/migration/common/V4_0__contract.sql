CREATE TABLE T_CONTRACT(
  id                      VARCHAR(36) NOT NULL,
  tenant_id               BIGINT NOT NULL DEFAULT 1,

  number                  VARCHAR(20) NOT NULL,
  type                    VARCHAR(30) DEFAULT NULL,
  status                  VARCHAR(30) DEFAULT NULL,
  content_url             TEXT NOT NULL,

  valid_from              DATETIME NOT NULL,
  valid_until             DATETIME NOT NULL,
  created_at              DATETIME DEFAULT NOW(),
  signed_at               DATETIME DEFAULT NULL,

  CONSTRAINT FK_CONTRACT_tenant FOREIGN KEY(tenant_id) REFERENCES T_TENANT(id),
  UNIQUE(tenant_id, number),
  PRIMARY KEY(id)
) ENGINE = InnoDB;

CREATE INDEX I_CONTRACT_tenant ON T_CONTRACT(tenant_id);

CREATE TABLE T_CONTRACT_PARTY(
  id                      VARCHAR(36) NOT NULL,
  tenant_id               BIGINT NOT NULL DEFAULT 1,

  contract_id             VARCHAR(36) NOT NULL,
  party_id                BIGINT NOT NULL,

  role                    VARCHAR(30) DEFAULT NULL,
  legal_name              VARCHAR(255) NOT NULL,
  legal_email             VARCHAR(255) NOT NULL,

  signed                  BOOLEAN NOT NULL DEFAULT false,
  ip_address              VARCHAR(45) DEFAULT NULL,
  user_agent              TEXT DEFAULT NULL,
  signed_at               DATETIME DEFAULT NULL,

  CONSTRAINT FK_CONTRACT_PARTY_contract FOREIGN KEY(contract_id) REFERENCES T_CONTRACT(id),
  CONSTRAINT FK_CONTRACT_PARTY_party FOREIGN KEY(party_id) REFERENCES T_PARTY(id),
  CONSTRAINT FK_CONTRACT_PARTY_tenant FOREIGN KEY(tenant_id) REFERENCES T_TENANT(id),
  PRIMARY KEY(id)
) ENGINE = InnoDB;

CREATE INDEX I_CONTRACT_PARTY_contract ON T_CONTRACT_PARTY(contract_id);
CREATE INDEX I_CONTRACT_PARTY_party ON T_CONTRACT_PARTY(party_id);

-- Last contract number of each tenant, prefix and year (ex: BAIL-2026-000042), see ContractNumberGenerator
CREATE TABLE T_CONTRACT_SEQUENCE(
  tenant_id               BIGINT NOT NULL,
  prefix                  VARCHAR(10) NOT NULL,
  year                    INT NOT NULL,
  value                   BIGINT NOT NULL,

  CONSTRAINT FK_CONTRACT_SEQUENCE_tenant FOREIGN KEY(tenant_id) REFERENCES T_TENANT(id),
  PRIMARY KEY(tenant_id, prefix, year)
) ENGINE = InnoDB;
