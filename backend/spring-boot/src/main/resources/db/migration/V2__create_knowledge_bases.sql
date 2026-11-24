CREATE TABLE core.knowledge_bases (
                                      id uuid PRIMARY KEY,

                                      name varchar(50),

                                      name_key text
                                          GENERATED ALWAYS AS (lower(name COLLATE "C"))
                                          STORED,

                                      description varchar(500),

                                      created_by uuid NOT NULL,

                                      lifecycle varchar(20) NOT NULL,

                                      delete_error_code text,
                                      delete_error_detail text,

                                      created_at timestamptz NOT NULL DEFAULT now(),
                                      updated_at timestamptz NOT NULL DEFAULT now(),
                                      deleted_at timestamptz,

                                      CONSTRAINT fk_kb_created_by
                                          FOREIGN KEY (created_by)
                                              REFERENCES core.users (id)
                                              ON DELETE RESTRICT,

                                      CONSTRAINT ck_kb_lifecycle
                                          CHECK (
                                              lifecycle IN (
                                                            'ACTIVE', 'DELETING', 'DELETE_FAILED', 'DELETED'
                                                  )
                                              ),

                                      CONSTRAINT ck_kb_live_name
                                          CHECK (
                                              lifecycle = 'DELETED'
                                                  OR (
                                                  name IS NOT NULL
                                                      AND char_length(name) BETWEEN 1 AND 50
                                                      AND name !~ '^[\x01-\x20]|[\x01-\x20]$'
                                                  )
                                              ),

                                      CONSTRAINT ck_kb_deletion_metadata
                                          CHECK (
                                              (
                                                  lifecycle <> 'DELETED'
                                                      AND deleted_at IS NULL
                                                  )
                                                  OR (
                                                  lifecycle = 'DELETED'
                                                      AND deleted_at IS NOT NULL
                                                      AND name IS NULL
                                                      AND description IS NULL
                                                      AND delete_error_code IS NULL
                                                      AND delete_error_detail IS NULL
                                                  )
                                              )
);

CREATE UNIQUE INDEX uq_live_kb_name
    ON core.knowledge_bases (name_key)
    WHERE lifecycle <> 'DELETED';

CREATE INDEX idx_kb_created_by
    ON core.knowledge_bases (created_by);