CREATE TABLE T_CUSTOMER(
  id                      BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id               BIGINT NOT NULL DEFAULT 1,

  party_id                BIGINT NOT NULL,

  whatsapp_number         VARCHAR(20),
  created_at              DATETIME DEFAULT NOW(),
  modified_at             DATETIME NOT NULL DEFAULT now() ON UPDATE now(),

  UNIQUE (party_id),

  CONSTRAINT FK_CUSTOMER_party FOREIGN KEY(party_id) REFERENCES T_PARTY(id),
  PRIMARY KEY(id)
) ENGINE = InnoDB;

CREATE INDEX I_CUSTOMER_tenant ON T_CUSTOMER(tenant_id);
