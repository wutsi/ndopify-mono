CREATE TABLE T_ASSIGNMENT(
    id                      BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id               BIGINT NOT NULL DEFAULT 1,

    mandate_id              BIGINT NOT NULL,
    agent_id                BIGINT NOT NULL,

    status                  VARCHAR(30) DEFAULT NULL,
    score                   DOUBLE NOT NULL DEFAULT 0.0,

    created_at              DATETIME DEFAULT NOW(),
    modified_at             DATETIME NOT NULL DEFAULT now() ON UPDATE now(),
    expires_at              DATETIME NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,

    INDEX IDX_ASSIGNMENT_mandate_status (mandate_id, status),
    INDEX IDX_ASSIGNMENT_agent_status (agent_id, status),
    INDEX IDX_ASSIGNMENT_status_expires (status, expires_at),

    CONSTRAINT FK_ASSIGNMENT_mandate FOREIGN KEY(mandate_id) REFERENCES T_MANDATE(id),
    CONSTRAINT FK_ASSIGNMENT_agent FOREIGN KEY(agent_id) REFERENCES T_AGENT(id),
    CONSTRAINT FK_ASSIGNMENT_tenant FOREIGN KEY(tenant_id) REFERENCES T_TENANT(id),
    PRIMARY KEY(id)
) ENGINE = InnoDB;

ALTER TABLE T_AGENT ADD COLUMN last_assigned_at DATETIME;

-- 1..5 stars; 3 = unrated (neutral in the dispatch score)
ALTER TABLE T_AGENT ADD COLUMN rating DOUBLE NOT NULL DEFAULT 3.0;
