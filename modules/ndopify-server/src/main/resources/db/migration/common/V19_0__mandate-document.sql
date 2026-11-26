CREATE TABLE T_MANDATE_DOCUMENT(
  id                BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id         BIGINT NOT NULL,
  mandate_id        BIGINT NOT NULL,

  type              VARCHAR(30) DEFAULT NULL,
  title             VARCHAR(100) NOT NULL,
  status            VARCHAR(30) DEFAULT NULL,
  problem_codes     TEXT,
  data              JSON,
  data_version      INT,
  issued_at         DATE,
  expires_at        DATE,

  version           BIGINT NOT NULL DEFAULT 0,
  created_at        DATETIME NOT NULL DEFAULT NOW(),
  modified_at       DATETIME NOT NULL DEFAULT NOW() ON UPDATE NOW(),

  CONSTRAINT FK_MANDATE_DOCUMENT_mandate FOREIGN KEY(mandate_id) REFERENCES T_MANDATE(id),
  CONSTRAINT FK_MANDATE_DOCUMENT_tenant FOREIGN KEY(tenant_id) REFERENCES T_TENANT(id),
  PRIMARY KEY(id)
) ENGINE = InnoDB;

CREATE INDEX I_MANDATE_DOCUMENT_mandate ON T_MANDATE_DOCUMENT(tenant_id, mandate_id);
CREATE INDEX I_MANDATE_DOCUMENT_status ON T_MANDATE_DOCUMENT(status);


CREATE TABLE T_MANDATE_DOCUMENT_PAGE(
  id                BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id         BIGINT NOT NULL,
  document_id       BIGINT NOT NULL,

  page_index        INT NOT NULL,
  path              VARCHAR(255) NOT NULL,
  storage_type      VARCHAR(30) DEFAULT NULL,
  mime_type         VARCHAR(50) DEFAULT NULL,

  created_at        DATETIME NOT NULL DEFAULT NOW(),
  modified_at       DATETIME NOT NULL DEFAULT NOW() ON UPDATE NOW(),

  -- The paths are UUID-based, so they're unique globally; a document's pages are numbered 0..n with no gap
  UNIQUE (document_id, page_index),
  UNIQUE (path),

  CONSTRAINT FK_MANDATE_DOCUMENT_PAGE_document FOREIGN KEY(document_id) REFERENCES T_MANDATE_DOCUMENT(id),
  CONSTRAINT FK_MANDATE_DOCUMENT_PAGE_tenant FOREIGN KEY(tenant_id) REFERENCES T_TENANT(id),
  PRIMARY KEY(id)
) ENGINE = InnoDB;
