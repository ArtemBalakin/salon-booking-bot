-- Какие мастера выполняют какие услуги
CREATE TABLE master_service (
    master_id   BIGINT NOT NULL REFERENCES master (id),
    service_id  BIGINT NOT NULL REFERENCES service (id),
    PRIMARY KEY (master_id, service_id)
);

-- Демо-мастера: специализации
INSERT INTO master_service (master_id, service_id)
SELECT m.id, s.id
FROM master m
JOIN service s ON
    (m.name = 'Айгерим' AND s.name IN ('Женская стрижка', 'Мужская стрижка', 'Окрашивание'))
 OR (m.name = 'Динара'  AND s.name IN ('Женская стрижка', 'Маникюр (гель-лак)'));

-- Остальные мастера (реальные данные, добавленные руками) пока умеют всё,
-- чтобы после миграции не пропали из выдачи
INSERT INTO master_service (master_id, service_id)
SELECT m.id, s.id
FROM master m
CROSS JOIN service s
WHERE NOT EXISTS (SELECT 1 FROM master_service ms WHERE ms.master_id = m.id);

-- Жизненный цикл записи: PENDING -> CONFIRMED -> (CANCELLED | REJECTED)
-- Отменённая запись больше не должна блокировать слот, поэтому уникальность
-- slot_id действует только для активных записей.
ALTER TABLE booking DROP CONSTRAINT booking_slot_id_key;
CREATE UNIQUE INDEX uq_booking_active_slot ON booking (slot_id) WHERE status IN ('PENDING', 'CONFIRMED');
CREATE INDEX idx_booking_user ON booking (telegram_user_id, status);

-- Напоминания клиентам
ALTER TABLE booking ADD COLUMN reminder_24h_sent BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE booking ADD COLUMN reminder_2h_sent  BOOLEAN NOT NULL DEFAULT FALSE;
