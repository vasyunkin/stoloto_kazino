# Гайд эксперта: «Воздушный Шар» (backend)

Документ для жюри и тюнинга параметров **без** чтения всего кода.  
Контракт API: [Swagger UI](http://localhost:8080/swagger-ui.html). Спеки: `.specs/0-backend-technical-spec.md`, `.specs/1-implementation-slices.md`, `.specs/2-post-s9-backlog-and-slices.md`.

---

## 1. Быстрый demo

1. `docker compose up --build`
2. Health: `http://localhost:8080/actuator/health` → `UP`
3. Swagger → **Authorize**: PlayerId=`demo`, AdminKey=`change-me-in-prod`
4. `POST /api/players/demo/deposit` → `{"amount": 1000}`
5. `POST /api/game/start` → скопировать `gameId`
6. Poll `GET /api/game/state/{gameId}` каждые **100–250 ms**
7. `POST /api/game/cashout/{gameId}` или дождаться `CRASHED`
8. `GET /api/game/verify/{gameId}` — PF
9. `GET /api/game/history` — лента завершённых раундов (без секретов)

Подробнее: корневой `README.md`.

---

## 2. Что можно крутить (YAML / `PUT /api/admin/config`)

Partial merge JSON. Ключ админки в JSON **не** отдаётся и через PUT **не** меняется.

| Поле | Default | Ограничения валидации | Эффект |
|------|---------|----------------------|--------|
| `greenLevels` | 9 | `@Positive` | Высота зелёной зоны (линии) |
| `redLevels` | 12 | `@Positive` | Высота красной зоны |
| `pointsPerLine` | 10 | `@Positive` | Очки за новую линию |
| `ascentSpeedLinesPerSec` | 1.5 | `@Positive` | Скорость набора линий |
| `math.growthRate` | 0.065 | `@Positive` | \(r\) в \(K(t)=e^{rt}\) (эквивалент формулы в коде) |
| `math.houseEdge` | 0.04 | `[0, 0.5]` | House edge PF / crash draw |
| `math.instantCrashRate` | 0.03 | `[0, 1]` | Шанс краша на 1.00x |
| `boosters.spawnProbability` | 0.70 | `[0, 1]` | Шанс спавна бустера |
| `boosters.tiers[]` | 3 тира | непустой список; weight/multiplier `@Positive` | Веса и множители очков бустера |
| `scoring.redZoneMultiplier` | 1.0 | `≥ 0` | Множитель очков в RED |
| `admin.minBetAmount` / `maxBetAmount` | 10 / 10000 | `@Positive` | Лимиты ставки |
| `admin.maxWinMultiplier` | 100 | `@Positive` | Cap crash multiplier |
| `admin.allowDeposit` | true | boolean | Демо-deposit; runtime через `ConfigService` (S10) |

Пример:

```http
PUT /api/admin/config
X-Admin-Key: change-me-in-prod
Content-Type: application/json

{"math":{"growthRate":0.08},"admin":{"allowDeposit":false}}
```

---

## 3. Сценарии приёмки (1–5)

| # | Сценарий | Ожидание |
|---|----------|----------|
| 1 | Рост \(K\) | Poll state: `multiplier` растёт со временем при том же `gameId` |
| 2 | Crash | При \(K \ge crashPoint\) → `CRASHED`, ставка не возвращается; в state есть `crashPoint`/`serverSeed` |
| 3 | Cashout | До краша → `CASHED_OUT`, `winAmount = bet × K` (scale 4 HALF_UP) |
| 4 | Booster | Если выпал: `booster` в state; очки с бонусом один раз при пересечении `triggerLine` |
| 5 | Config mid-flight (I8) | Start раунд A → PUT `growthRate` → Start раунд B → при том же \(t\) множители **разные**; раунд A не сдвигается |

Дополнительно: `allowDeposit: false` → deposit **403** без рестарта JVM. History не показывает `FLYING`. Пазл (`puzzlePieceIndex`) только на `CRASHED`/`CASHED_OUT`, не на `VOID`.

---

## 4. Чеклист ограничений (сдаточный)

- [ ] **Single-JVM.** Активные раунды в `ConcurrentHashMap`. Второй инстанс не видит чужой FLYING → 410 / recovery VOID.
- [ ] **Транспорт MVP = HTTP polling** 100–250 ms. WebSocket **запланирован (S14)**; зависимость `spring-boot-starter-websocket` уже в `pom.xml` (код канала ещё не подключён).
- [ ] **Restart:** все `FLYING` → `VOID` + `CREDIT_REFUND`. Полёт из БД не продолжается.
- [ ] **Deposit:** флаг `admin.allowDeposit` через runtime snapshot (`ConfigService`), не «только YAML до рестарта».
- [ ] **Rate limit:** 40 / 10 с на start и cashout; in-memory; в тестах выключен. Не кластерный.
- [ ] **I1:** пока `FLYING`, нет `crashPoint` / `serverSeed` (и нет `puzzlePieceIndex`).
- [ ] **I8:** летящий раунд читает только snapshot со start, не live PUT.
- [ ] **Verify:** только terminal; иначе 409 `ROUND_NOT_TERMINAL`.
- [ ] **Деньги:** только `BigDecimal` + ledger + `SELECT FOR UPDATE` первым load Player в write-TX.
- [ ] **Cashout ≠ «долететь до взрыва» на сервере:** статус сразу `CASHED_OUT`; визуальный долёт — задача фронта.
- [ ] **Nonce** процесс-локальный; после рестарта может пересечься — PF уникален новым `serverSeed`.
- [ ] **429** пишет rate-limit filter своим JSON (не через `GlobalExceptionHandler`).

---

## 5. Provably Fair (кратко)

Алгоритм: **`crash-v1`**.  
На start клиент получает `commitHash`. После конца раунда `GET /verify`: `serverSeed`, `clientSeed`, `nonce`, `crashPoint`.  
Проверка: `SHA-256(serverSeed|clientSeed|nonce) == commitHash`, затем пересчёт crash по HMAC (детали в verify / Spec 0).

---

## 6. Пазл (S12)

```text
material = serverSeed + ":" + status + ":" + outcomeKey
outcomeKey = CASHED_OUT → cashoutMultiplier (scale 4)
             CRASHED    → "CRASH"
pieceIndex = unsigned(SHA-256(material)[0..3]) % 24
```

Не влияет на баланс кошелька.

---

## 7. Связанные спеки

| Файл | Содержание |
|------|------------|
| `.specs/0-backend-technical-spec.md` | Архитектура, API, схема БД, PF |
| `.specs/1-implementation-slices.md` | S1–S9 + инварианты I1–I8 |
| `.specs/2-post-s9-backlog-and-slices.md` | Техдолг, S10+ (history, puzzle, WS…) |
