CREATE TABLE T_ASSIGNMENT(
    id                      BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id               BIGINT NOT NULL DEFAULT 1,

    mandate_id              BIGINT NOT NULL,
    agent_id                BIGINT NOT NULL,

    status                  INT NOT NULL DEFAULT 0,
    score                   DOUBLE NOT NULL DEFAULT 0.0,

    created_at              DATETIME DEFAULT NOW(),
    modified_at             DATETIME NOT NULL DEFAULT now() ON UPDATE now(),
    expires_at              DATETIME NOT NULL,

    CONSTRAINT FK_ASSIGNMENT_mandate FOREIGN KEY(mandate_id) REFERENCES T_MANDATE(id),
    CONSTRAINT FK_ASSIGNMENT_agent FOREIGN KEY(agent_id) REFERENCES T_AGENT(id),
    CONSTRAINT FK_ASSIGNMENT_tenant FOREIGN KEY(tenant_id) REFERENCES T_TENANT(id),
    PRIMARY KEY(id)
) ENGINE = InnoDB;

ALTER TABLE T_AGENT ADD COLUMN last_assigned_at DATETIME;
