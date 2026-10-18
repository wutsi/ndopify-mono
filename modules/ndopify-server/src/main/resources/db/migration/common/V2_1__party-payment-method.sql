CREATE TABLE T_PAYMENT_METHOD(
  id                      VARCHAR(36) NOT NULL,
  tenant_id               BIGINT NOT NULL DEFAULT 1,
  party_id                BIGINT NOT NULL,

  number                  VARCHAR(20) NOT NULL,
  provider_name           VARCHAR(100),
  holder_name             VARCHAR(100),
  expires_at              DATE,
  type                    VARCHAR(30) DEFAULT NULL,
  status                  VARCHAR(30) DEFAULT NULL,

  created_at              DATETIME DEFAULT NOW(),
  modified_at             DATETIME NOT NULL DEFAULT now() ON UPDATE now(),

  UNIQUE(party_id, number, type),

  CONSTRAINT FK_PAYMENT_METHOD_party FOREIGN KEY(party_id) REFERENCES T_PARTY(id),
  CONSTRAINT FK_PAYMENT_METHOD_tenant FOREIGN KEY(tenant_id) REFERENCES T_TENANT(id),
  PRIMARY KEY(id)
) ENGINE = InnoDB;
CREATE INDEX I_PAYMENT_METHOD_tenant ON T_PAYMENT_METHOD(tenant_id);
