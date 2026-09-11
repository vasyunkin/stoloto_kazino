# Воздушный Шар — backend

Crash-игра: Java 17+ / Spring Boot 3.4 / PostgreSQL 16.

## Запуск

```bash
docker compose up --build
```

Postgres поднимается с healthcheck; backend ждёт `healthy`. Health: `GET /actuator/health`.

Демо-пополнение (пока `game.admin.allow-deposit=true`):

```bash
curl -X POST http://localhost:8080/api/players/demo/deposit \
  -H 'Content-Type: application/json' \
  -d '{"amount": 1000}'
```

Игра: `POST /api/game/start` → poll `GET /api/game/state/{gameId}` → `POST /api/game/cashout/{gameId}`.
Заголовок `X-Player-Id` должен совпадать с `playerId` в ставке.

## Рестарт сервера (S7)

Все раунды со статусом `FLYING` при старте процесса переводятся в `VOID`, ставка возвращается (`CREDIT_REFUND`).
Активный полёт в памяти не восстанавливается: cache miss по `FLYING` → `410 ROUND_EXPIRED`.

## Provably Fair (`crash-v1`)

После `CRASHED` / `CASHED_OUT` (или `VOID` после recovery):

```bash
curl http://localhost:8080/api/game/verify/{gameId} -H 'X-Player-Id: demo'
```

Ответ: `serverSeed`, `clientSeed`, `nonce`, `commitHash`, `crashPoint`, `algorithmVersion`.

Проверка вручную:

1. `commitHash == SHA-256( serverSeed + "|" + clientSeed + "|" + nonce )` (hex, lowercase).
2. `h = HMAC-SHA256(key=serverSeedBytes, msg=clientSeed + ":" + nonce)`.
3. `u = first52bits(h) / 2^52`. Если `u < instant-crash-rate` → `crashPoint = 1.0000`, иначе  
   `min(maxWin, max(1.0, (1 - houseEdge) / u))` — должно совпасть с `crashPoint` из verify.

Пока `status = FLYING`, seed и crashPoint в API нет (`I1`). Verify до конца раунда → `409 ROUND_NOT_TERMINAL`.
