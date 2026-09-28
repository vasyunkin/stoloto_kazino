# ADR-001: Монолит Spring Boot и server-authoritative crash

- **Статус:** Принято
- **Дата:** 2026-03-28
- **Контекст:** Нужна учебно-демонстрационная и продуктово-цельная crash-ИС: честный краш, real-time полёт (~200 мс), кошелёк, админка без редеплоя, срок сдачи ограничен. Альтернативы: (1) микросервисы (game / wallet / config), (2) расчёт K на клиенте с «проверкой» постфактум, (3) server-authoritative монолит.

## Решение

- **Один deployable backend** (Java 21, Spring Boot 3.4): REST + STOMP, единый `RoundLifecycleService` для REST и WebSocket.
- **Server-authoritative:** crash point, seed, cashout и баланс только на сервере; provably fair (`crash-v1`, commit на старте, verify после терминала).
- **PostgreSQL** для персистентности игрока, раундов, ledger, аудита конфига; **активные FLYING-раунды** — в памяти JVM (`ConcurrentHashMap` + lock на `gameId`).
- **Frontend:** SPA (React + PixiJS), nginx как reverse proxy; альбом пазла — клиентский `localStorage`.

## Последствия

### Положительные

- Быстрая разработка и одна команда `docker compose up` для жюри.
- Нет рассинхрона формулы между каналами — один код расчёта.
- Ledger и транзакции кошелька готовы к замене демо-депозита на платёжный шлюз.
- Live-config с аудитом без рестарта JVM.

### Отрицательные / риски

- Горизонтальное масштабирование API **не поддерживается** без shared store для FLYING (второй инстанс не видит чужой полёт).
- Rate limit in-memory — не кластерный.
- Альбом не синхронизируется между устройствами.

### Дальнейшие шаги

- При росте нагрузки: Redis (или аналог) для session раунда + sticky или pub/sub тиков.
- Опционально: серверный альбом; JWT/сессия игрока вместо только `X-Player-Id`.
