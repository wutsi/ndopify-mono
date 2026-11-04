CREATE TABLE T_VISIT(
    id                          BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id                   BIGINT NOT NULL DEFAULT 1,

    match_id                    BIGINT NOT NULL,
    mandate_id                  BIGINT NOT NULL,
    listing_id                  BIGINT NOT NULL,
    agent_id                    BIGINT NOT NULL,
    customer_id                 BIGINT NOT NULL,

    status                      VARCHAR(30) DEFAULT NULL,
    type                        VARCHAR(30) DEFAULT NULL,
    requested_at                DATETIME NOT NULL,
    note                        TEXT DEFAULT NULL,
    scheduled_at                DATETIME DEFAULT NULL,

    -- The check-in code: only the visit's customer ever reads it
    code                        VARCHAR(4) DEFAULT NULL,
    code_attempts               INT NOT NULL DEFAULT 0,

    checked_in_at               DATETIME DEFAULT NULL,
    check_in_distance_meters    INT DEFAULT NULL,
    check_in_flagged            BOOLEAN NOT NULL DEFAULT FALSE,
    check_in_latitude           DOUBLE DEFAULT NULL,
    check_in_longitude          DOUBLE DEFAULT NULL,
    check_in_accuracy           DOUBLE DEFAULT NULL,

    checked_out_at              DATETIME DEFAULT NULL,
    check_out_latitude          DOUBLE DEFAULT NULL,
    check_out_longitude         DOUBLE DEFAULT NULL,
    check_out_accuracy          DOUBLE DEFAULT NULL,
    agent_comment               TEXT DEFAULT NULL,

    interest                    VARCHAR(30) DEFAULT NULL,
    feedback_reason_code        VARCHAR(30) DEFAULT NULL,
    feedback_comment            VARCHAR(1000) DEFAULT NULL,

    no_show_party               VARCHAR(30) DEFAULT NULL,
    no_show_reason              VARCHAR(30) DEFAULT NULL,
    no_show_latitude            DOUBLE DEFAULT NULL,
    no_show_longitude           DOUBLE DEFAULT NULL,
    no_show_accuracy            DOUBLE DEFAULT NULL,

    cancelled_by                VARCHAR(30) DEFAULT NULL,
    cancellation_reason         TEXT DEFAULT NULL,
    cancelled_at                DATETIME DEFAULT NULL,

    reminder_sent_at            DATETIME DEFAULT NULL,
    created_at                  DATETIME NOT NULL DEFAULT NOW(),
    version                     BIGINT NOT NULL DEFAULT 0,

    INDEX IDX_VISIT_status_scheduled (tenant_id, status, scheduled_at),
    INDEX IDX_VISIT_match_status (match_id, status),

    CONSTRAINT FK_VISIT_match FOREIGN KEY(match_id) REFERENCES T_MATCH(id),
    CONSTRAINT FK_VISIT_mandate FOREIGN KEY(mandate_id) REFERENCES T_MANDATE(id),
    CONSTRAINT FK_VISIT_listing FOREIGN KEY(listing_id) REFERENCES T_LISTING(id),
    CONSTRAINT FK_VISIT_tenant FOREIGN KEY(tenant_id) REFERENCES T_TENANT(id),
    PRIMARY KEY(id)
) ENGINE = InnoDB;
