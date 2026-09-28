# ADR-002: PostgreSQL 16 и STOMP WebSocket с REST fallback

- **Статус:** Принято
- **Дата:** 2026-03-28
- **Контекст:** Нужны ACID для кошелька, история раундов для рейтинга и verify, миграции схемы. Real-time — плавный полёт; сеть жюри/мобилы может рвать WebSocket. Альтернативы: MongoDB; только REST long-poll; только WebSocket без fallback; Redis как primary DB.

## Решение

- **PostgreSQL 16** + JPA + Flyway: `player`, `game_round`, `wallet_ledger`, `config_snapshot`, admin-таблицы.
- Деньги: `NUMERIC(19,4)`, `BigDecimal` в коде, `SELECT FOR UPDATE` на игроке в write-транзакциях.
- **STOMP** на `/ws`, тики **200 мс**; клиент `@stomp/stompjs`.
- **Fallback:** если WS не поднялся за **1,5 с** или тики молчат **> 1 с** — `GET /api/game/state/{gameId}` каждые **200 мс**.

## Последствия

### Положительные

- Проверенная модель транзакций для ставок и выплат.
- Testcontainers Postgres в интеграционных тестах.
- Демо устойчиво на нестабильном Wi‑Fi.

### Отрицательные

- Write-нагрузка на Postgres растёт с числом **завершённых** раундов, не с частотой тиков (тики — память + WS).
- Два транспорта поддерживать и тестировать (REST + STOMP).
