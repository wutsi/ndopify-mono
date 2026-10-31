CREATE TABLE T_MATCH(
    id                      BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id               BIGINT NOT NULL DEFAULT 1,

    mandate_id              BIGINT NOT NULL,
    listing_id              BIGINT NOT NULL,
    agent_id                BIGINT NOT NULL,

    status                  VARCHAR(30) DEFAULT NULL,
    score                   DOUBLE NOT NULL DEFAULT 0.0,
    neighborhood_score      DOUBLE NOT NULL DEFAULT 0.0,
    price_score             DOUBLE NOT NULL DEFAULT 0.0,
    rooms_score             DOUBLE NOT NULL DEFAULT 0.0,
    amenities_score         DOUBLE NOT NULL DEFAULT 0.0,
    availability_score      DOUBLE NOT NULL DEFAULT 0.0,
    furniture_score         DOUBLE NOT NULL DEFAULT 0.0,
    listing_price           BIGINT NOT NULL DEFAULT -1,

    created_at              DATETIME DEFAULT NOW(),
    modified_at             DATETIME NOT NULL DEFAULT now() ON UPDATE now(),
    version                 BIGINT NOT NULL DEFAULT 0,

    UNIQUE(mandate_id, listing_id),
    INDEX IDX_MATCH_mandate_status (mandate_id, status),
    INDEX IDX_MATCH_listing (listing_id),

    CONSTRAINT FK_MATCH_mandate FOREIGN KEY(mandate_id) REFERENCES T_MANDATE(id),
    CONSTRAINT FK_MATCH_listing FOREIGN KEY(listing_id) REFERENCES T_LISTING(id),
    CONSTRAINT FK_MATCH_agent FOREIGN KEY(agent_id) REFERENCES T_AGENT(id),
    CONSTRAINT FK_MATCH_tenant FOREIGN KEY(tenant_id) REFERENCES T_TENANT(id),
    PRIMARY KEY(id)
) ENGINE = InnoDB;
