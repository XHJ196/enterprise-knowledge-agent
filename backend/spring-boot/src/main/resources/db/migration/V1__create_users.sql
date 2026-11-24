CREATE TABLE core.users (
                            id uuid PRIMARY KEY,

                            username varchar(32) NOT NULL,

                            username_key text
                                GENERATED ALWAYS AS (lower(username COLLATE "C"))
                                STORED NOT NULL,

                            password_hash text NOT NULL,

                            role varchar(16) NOT NULL,

                            created_at timestamptz NOT NULL DEFAULT now(),
                            updated_at timestamptz NOT NULL DEFAULT now(),

                            CONSTRAINT uq_users_username_key
                                UNIQUE (username_key),

                            CONSTRAINT ck_users_username
                                CHECK (username ~ '^[A-Za-z0-9_]{3,32}$'),

    CONSTRAINT ck_users_role
        CHECK (role IN ('ADMIN', 'EMPLOYEE'))
);