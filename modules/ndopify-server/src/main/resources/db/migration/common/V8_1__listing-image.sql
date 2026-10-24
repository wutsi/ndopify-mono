CREATE TABLE T_LISTING_IMAGE(
  id                      BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id               BIGINT NOT NULL DEFAULT 1,

  listing_id              BIGINT NOT NULL,
  path                    VARCHAR(255) NOT NULL,
  url                     VARCHAR(1024) NOT NULL,
  mime_type               VARCHAR(100),
  status                  VARCHAR(30) DEFAULT NULL,
  error_message           TEXT,

  created_at              DATETIME DEFAULT NOW(),
  modified_at             DATETIME NOT NULL DEFAULT now() ON UPDATE now(),

  CONSTRAINT FK_LISTING_IMAGE_tenant FOREIGN KEY(tenant_id) REFERENCES T_TENANT(id),
  CONSTRAINT FK_LISTING_IMAGE_listing FOREIGN KEY(listing_id) REFERENCES T_LISTING(id) ON DELETE CASCADE,
  PRIMARY KEY(id)
) ENGINE = InnoDB;

CREATE INDEX I_LISTING_IMAGE_listing ON T_LISTING_IMAGE(listing_id);
