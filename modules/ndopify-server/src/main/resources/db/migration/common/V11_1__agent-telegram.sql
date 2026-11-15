CREATE TABLE T_AGENT_TELEGRAM(
  id                BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id         BIGINT NOT NULL,
  agent_id          BIGINT NOT NULL,

  code              VARCHAR(6) DEFAULT NULL,
  code_expires_at   DATETIME DEFAULT NULL,
  chat_id           BIGINT DEFAULT NULL,
  linked_at         DATETIME DEFAULT NULL,

  created_at        DATETIME NOT NULL DEFAULT NOW(),
  modified_at       DATETIME NOT NULL DEFAULT NOW() ON UPDATE NOW(),

  -- NULLs are allowed to repeat in a MySQL unique index: only issued codes and linked chats must be unique.
  UNIQUE (agent_id),
  UNIQUE (code),
  UNIQUE (chat_id),

  CONSTRAINT FK_AGENT_TELEGRAM_agent FOREIGN KEY(agent_id) REFERENCES T_AGENT(id),
  CONSTRAINT FK_AGENT_TELEGRAM_tenant FOREIGN KEY(tenant_id) REFERENCES T_TENANT(id),
  PRIMARY KEY(id)
) ENGINE = InnoDB;

CREATE INDEX I_AGENT_TELEGRAM_tenant ON T_AGENT_TELEGRAM(tenant_id);
