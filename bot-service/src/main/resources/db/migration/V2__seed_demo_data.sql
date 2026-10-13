INSERT INTO service (name, duration_minutes, price_tenge) VALUES
    ('Женская стрижка', 60, 8000),
    ('Мужская стрижка', 45, 5000),
    ('Маникюр (гель-лак)', 90, 9000),
    ('Окрашивание', 120, 18000);

INSERT INTO master (name) VALUES
    ('Айгерим'),
    ('Динара');

-- Свободные слоты на ближайшие 14 дней, с 10:00 до 19:00, шаг 1 час, для каждого мастера
INSERT INTO slot (master_id, start_time, end_time, is_booked)
SELECT m.id,
       day::date + t.hour * INTERVAL '1 hour' AS start_time,
       day::date + (t.hour + 1) * INTERVAL '1 hour' AS end_time,
       FALSE
FROM master m
CROSS JOIN generate_series(CURRENT_DATE, CURRENT_DATE + INTERVAL '13 days', INTERVAL '1 day') AS day
CROSS JOIN generate_series(10, 18) AS t(hour)
ON CONFLICT (master_id, start_time) DO NOTHING;
