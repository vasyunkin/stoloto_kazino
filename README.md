# Воздушный Шар — backend

Crash-игра: Java 21 / Spring Boot 3.4 / PostgreSQL 16.

Контракт для фронта: **OpenAPI** (`/swagger-ui.html`, JSON `/v3/api-docs`) + этот README. Новых игровых фич в API нет — только описанные эндпоинты.

## Запуск

Полный стек (Postgres + backend):

```bash
docker compose up --build
```

Postgres в compose проброшен на хост как **5433** (`5433:5432`). Контейнер backend ходит на `postgres:5432`. Health: `GET http://localhost:8080/actuator/health`.

Только БД, backend локально:

```bash
docker compose up postgres
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/balloon_game \
SPRING_DATASOURCE_USERNAME=balloon \
SPRING_DATASOURCE_PASSWORD=balloon \
./mvnw spring-boot:run
```

По умолчанию `application.yml` смотрит на `localhost:5432`. Если Postgres только из compose — передайте URL с портом **5433**.

## OpenAPI

| Что | URL |
|-----|-----|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |

В UI для `/api/game/**` укажите заголовок `X-Player-Id`. Для `/api/admin/**` — `X-Admin-Key` (схема **AdminKey**).

## Типичный игровой цикл

Заголовок `X-Player-Id` обязателен на всех `/api/game/**` и **должен совпадать** с `playerId` в ставке. Чужой `gameId` → `403 FORBIDDEN` (не 404).

Poll `GET /state` каждые **100–250 ms**. Пока `status=FLYING`, в ответе **нет** `crashPoint` и `serverSeed`.

```bash
BASE=http://localhost:8080
PLAYER=demo

# 1. Демо-пополнение (пока game.admin.allow-deposit=true)
curl -s -X POST "$BASE/api/players/$PLAYER/deposit" \
  -H 'Content-Type: application/json' \
  -d '{"amount": 1000}'

curl -s "$BASE/api/players/$PLAYER/balance"

# 2. Старт раунда
START=$(curl -s -X POST "$BASE/api/game/start" \
  -H 'Content-Type: application/json' \
  -H "X-Player-Id: $PLAYER" \
  -d "{\"playerId\":\"$PLAYER\",\"betAmount\":50,\"balloonType\":\"STANDARD\",\"boosterPreference\":\"AUTO\"}")
echo "$START"
GAME_ID=$(echo "$START" | python3 -c "import sys,json; print(json.load(sys.stdin)['gameId'])")

# 3. Poll (в UI — таймер 100–250 ms, пока status=FLYING)
curl -s "$BASE/api/game/state/$GAME_ID" -H "X-Player-Id: $PLAYER"

# 4. Cashout, пока шар в полёте и K < crashPoint
curl -s -X POST "$BASE/api/game/cashout/$GAME_ID" -H "X-Player-Id: $PLAYER"

# 5. Provably Fair (только после CRASHED / CASHED_OUT / VOID)
curl -s "$BASE/api/game/verify/$GAME_ID" -H "X-Player-Id: $PLAYER"
```

`POST /start` возвращает `gameId`, `commitHash`, `serverSeedHash`, `startedAt`. Секреты раунда не отдаются.

## Заголовки

| Заголовок | Где | Зачем |
|-----------|-----|--------|
| `X-Player-Id` | `/api/game/**` | Идентификатор игрока (хакатон, вместо OAuth). Совпадает с `playerId`. |
| `X-Admin-Key` | `/api/admin/**` | Сравнение с `game.admin.api-key` / `GAME_ADMIN_KEY`. Неверный ключ → 403 без деталей. |
| `Content-Type: application/json` | POST/PUT с телом | |

## Ошибки

Тело: `{ "code", "message", "timestamp", "details"? }`.

| HTTP | code | Когда |
|------|------|--------|
| 400 | `VALIDATION_ERROR` | Нет заголовка, невалидное тело |
| 400 | `CONFIG_VALIDATION_FAILED` | Битый JSON / невалидный admin PUT |
| 400 | `INVALID_BET` | Ставка вне `[minBet, maxBet]` |
| 402 | `INSUFFICIENT_BALANCE` | Не хватает денег на ставку |
| 403 | `FORBIDDEN` | Чужой `gameId` / несовпадение `X-Player-Id` / плохой admin key |
| 403 | `DEPOSIT_NOT_ALLOWED` | `allow-deposit=false` |
| 404 | `PLAYER_NOT_FOUND` | Баланс несуществующего игрока |
| 409 | `ALREADY_CRASHED` / `ALREADY_CASHED_OUT` | Повторный cashout / шар уже упал |
| 409 | `ROUND_NOT_TERMINAL` | `verify` пока раунд летит |
| 410 | `ROUND_EXPIRED` | Cache miss по `FLYING` (рестарт / TTL) |
| 429 | `RATE_LIMITED` | Слишком частые `start`/`cashout` (см. ниже) |

## CORS

Whitelist, **не** `*`. По умолчанию:

- `http://localhost:3000`
- `http://localhost:5173`
- `http://127.0.0.1:3000`
- `http://127.0.0.1:5173`

Заголовки: `Content-Type`, `X-Player-Id`, `X-Admin-Key`. Список: `app.cors.allowed-origins`.

## Rate limit

На `POST /api/game/start` и `POST /api/game/cashout/{gameId}`: окно `app.rate-limit` (по умолчанию 40 запросов / 10 с на игрока). Ответ 429 `RATE_LIMITED`. В тестах выключено (`app.rate-limit.enabled=false`).

Это антиспам, не замена игровых локов.

## Admin

```bash
curl -s "$BASE/api/admin/config" -H "X-Admin-Key: $GAME_ADMIN_KEY"
curl -s -X PUT "$BASE/api/admin/config" \
  -H "X-Admin-Key: $GAME_ADMIN_KEY" \
  -H 'Content-Type: application/json' \
  -d '{"math":{"growthRate":0.07}}'
```

Deep merge. Поле `admin.apiKey` в JSON не отдаётся и через PUT не меняется. Уже начатый раунд использует снимок конфига со `start`, не «живой» YAML.

## Рестарт сервера

Все раунды `FLYING` при старте процесса → `VOID` + возврат ставки (`CREDIT_REFUND`). Полёт в памяти не восстанавливается: cache miss по `FLYING` → `410 ROUND_EXPIRED`.

## Provably Fair (`crash-v1`)

После `CRASHED` / `CASHED_OUT` (или `VOID` после recovery):

```bash
curl "$BASE/api/game/verify/{gameId}" -H "X-Player-Id: demo"
```

Ответ: `serverSeed`, `clientSeed`, `nonce`, `commitHash`, `crashPoint`, `algorithmVersion`.

Проверка вручную:

1. `commitHash == SHA-256( serverSeed + "|" + clientSeed + "|" + nonce )` (hex, lowercase).
2. `h = HMAC-SHA256(key=serverSeedBytes, msg=clientSeed + ":" + nonce)`.
3. `u = first52bits(h) / 2^52`. Если `u < instant-crash-rate` → `crashPoint = 1.0000`, иначе  
   `min(maxWin, max(1.0, (1 - houseEdge) / u))` — должно совпасть с `crashPoint` из verify.

Пока `status = FLYING`, seed и crashPoint в API нет. Verify до конца раунда → `409 ROUND_NOT_TERMINAL`.
