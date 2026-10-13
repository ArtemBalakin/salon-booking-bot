# Где размещать salon-booking-bot (проверено на 13.10.2026)

Проект состоит из Spring Boot сервиса (HTTP API + Telegram Mini App + Telegram **long polling** + задачи напоминаний) и PostgreSQL. Для корректной работы бот должен исполняться постоянно и иметь HTTPS-адрес Mini App. Для коммерческого использования нельзя полагаться на засыпающие сервисы: long polling и scheduler выключатся одновременно с процессом.

| Вариант | Цена (ориентир) | Насколько подходит |
|---|---|---|
| Oracle Cloud Always Free Ampere A1 | $0 при соблюдении лимитов | Лучшая попытка разместить постоянно бесплатно: VM + Postgres в Docker + HTTPS reverse proxy. Нужна регистрация с банковской картой; ресурсы могут отсутствовать; администрирование, бэкапы и HTTPS на тебе. На текущей странице Oracle указаны 1 500 OCPU-ч / 9 000 GB-ч для Arm в месяц (примерно до 2 OCPU/12 GB непрерывно). |
| Railway Free | $0, $5 одноразового кредита до 30 дней, далее $1/мес кредит | Отлично для прототипа. Кредита для постоянно работающих Java + Postgres обычно недостаточно. |
| Railway Hobby | минимум $5/мес с $5 включённого usage | Самый простой запуск без обслуживания VPS. При превышении включённого usage платишь за дополнительные ресурсы (Spring + Postgres может обойтись дороже $5). HTTPS и управление секретами в панели. |
| Hetzner CX23 (EU) | €5.49/мес + €0.50 за IPv4, без НДС | Предсказуемый бюджет за постоянные Spring + PostgreSQL + TLS в Docker. Администрирование и обновления на тебе; для HTTPS нужен домен/поддомен и reverse proxy (Caddy/Nginx). |
| Koyeb Free | $0 | **Нельзя для непрерывного long polling**: сервис обязательно засыпает через час без входящего трафика. |
| Render Free | $0 | **Нельзя для непрерывного long polling**: засыпает через 15 минут; бесплатный Postgres живёт только 30 дней. |
| Koyeb eco-small + Neon Free Postgres | от $5.36/мес + DB $0 при соблюдении лимитов | Альтернатива без собственного VPS. Eco-small 1 GB RAM; Neon Free предлагает 1 GB базы на проект, но спящий БД-compute и лимиты нагрузки. Оценить на реальных метриках. |

## Что выбрать

- **Не платить за разработку:** локальный Docker Compose. Для живого бесплатного тестового бота — Oracle Always Free, если пройдёт регистрация и будет доступная VM. Railway trial — самый быстрый временный демо-хостинг.
- **Для первых реальных клиентов без DevOps:** Railway Hobby, один сервис и отдельная БД. Настроить лимит расходов, доступность, резервирование БД.
- **Для постоянной работы с минимальной ценой:** Hetzner VPS за ~€6/мес без НДС (+ домен, если нужен) и Docker Compose за HTTPS proxy.

## Переменные окружения

`BOT_TOKEN`, `BOT_USERNAME`, `ADMIN_CHAT_ID`, `APP_BASE_URL=https://...`, `APP_TIMEZONE=Asia/Almaty`, `MANUAL_CONFIRMATION=true`, `SALON_NAME`, `SALON_ADDRESS`, `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`.

**Railway:** разместить `bot-service` как Docker-сервис, путь Dockerfile: `bot-service/Dockerfile`, корень сборки: `bot-service`; подключить отдельный PostgreSQL и подставить переменные DB_*, задать URL провайдера как APP_BASE_URL. `PORT` устанавливается платформой, в конфиге уже поддержан. Запускать **одну** реплику. Для полноценной оценки производительности тестировать на памяти не менее 512 MB, предпочтительно 1 GB.

**VPS:** Docker Compose уже содержит приложение и PostgreSQL с томом. Нельзя считать порт 8080 сам по себе публичным HTTPS: добавь TLS-прокси (например Caddy) и DNS для домена; выдавай наружу только 443/80, базу не публикуй. Делай регулярный `pg_dump` отдельно от контейнера и проверяй восстановление.

## Источники

- Oracle Always Free: https://www.oracle.com/cloud/free/
- Railway: https://docs.railway.com/pricing/plans ; https://docs.railway.com/pricing/free-trial
- Hetzner 2026: https://docs.hetzner.com/general/infrastructure-and-availability/price-adjustment/ ; https://docs.hetzner.com/cloud/servers/primary-ips/overview/
- Koyeb: https://www.koyeb.com/docs/reference/instances ; https://www.koyeb.com/docs/run-and-scale/scale-to-zero
- Render: https://render.com/docs/free
- Neon Free October 2026: https://neon.com/blog/neon-free-plan-1-gb-per-project
