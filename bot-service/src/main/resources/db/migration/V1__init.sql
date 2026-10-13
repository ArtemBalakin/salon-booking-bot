CREATE TABLE service (
    id                BIGSERIAL PRIMARY KEY,
    name              VARCHAR(120) NOT NULL,
    duration_minutes  INT NOT NULL,
    price_tenge       INT NOT NULL
);

CREATE TABLE master (
    id    BIGSERIAL PRIMARY KEY,
    name  VARCHAR(120) NOT NULL
);

CREATE TABLE slot (
    id          BIGSERIAL PRIMARY KEY,
    master_id   BIGINT NOT NULL REFERENCES master (id),
    start_time  TIMESTAMP NOT NULL,
    end_time    TIMESTAMP NOT NULL,
    is_booked   BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (master_id, start_time)
);

CREATE TABLE booking (
    id                  BIGSERIAL PRIMARY KEY,
    slot_id             BIGINT NOT NULL UNIQUE REFERENCES slot (id),
    service_id          BIGINT NOT NULL REFERENCES service (id),
    telegram_user_id    BIGINT NOT NULL,
    telegram_username   VARCHAR(120),
    client_name         VARCHAR(120) NOT NULL,
    client_phone        VARCHAR(40) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED',
    created_at          TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_slot_master_time ON slot (master_id, start_time);
CREATE INDEX idx_slot_free ON slot (is_booked, start_time);
