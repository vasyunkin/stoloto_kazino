# ADR-001: Стиль архитектуры — монолит и server-authoritative

- **Статус:** Принято
- **Дата:** 2026-03-28

## Контекст

Нужна crash-ИС с честным крашем, real-time (~200 мс), кошельком и live-config для оператора. Рассматривались: микросервисы (game / wallet / config), клиентский расчёт K, **монолит Spring Boot** с authoritative server.

## Решение

- **Монолит** Java 21 + Spring Boot 3.4: REST + STOMP, одна доменная логика раунда.
- **Server-authoritative** crash, cashout, баланс; provably fair (commit → verify).
- **PostgreSQL** — персистентность; **in-memory** — только FLYING-сессии.
- **React + nginx** — UI; альбом — localStorage.

## Последствия

- Быстрый deploy (Docker Compose), проще сдача демо и тесты.
- Горизонталь API потребует shared store для полётов (Redis — roadmap).
- Ledger готов к реальным платежам без смены жанра игры.

Подробнее: [001_monolith_server_authoritative.md](001_monolith_server_authoritative.md)
