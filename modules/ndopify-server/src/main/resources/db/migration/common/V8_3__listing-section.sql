CREATE TABLE T_LISTING_SECTION(
  id                      BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id               BIGINT NOT NULL DEFAULT 1,

  listing_id              BIGINT NOT NULL,
  section                 VARCHAR(30) NOT NULL,
  verified_at             DATETIME NOT NULL,

  UNIQUE(listing_id, section),
  CONSTRAINT FK_LISTING_SECTION_tenant FOREIGN KEY(tenant_id) REFERENCES T_TENANT(id),
  CONSTRAINT FK_LISTING_SECTION_listing FOREIGN KEY(listing_id) REFERENCES T_LISTING(id) ON DELETE CASCADE,
  PRIMARY KEY(id)
) ENGINE = InnoDB;
