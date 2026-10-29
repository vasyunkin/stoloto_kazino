# Тесты и покрытие — модуль 4

## Backend (JUnit 5, AssertJ, Mockito, Testcontainers)

### Запуск

```bash
# Все тесты (интеграционные требуют Docker)
./mvnw test

# Только unit-тесты без Testcontainers
./mvnw test -Dtest=ProvablyFairServiceTest,CrashMathServiceTest,BoosterScoringTest,ConfigServiceTest,PuzzleServiceTest,GameClockServiceTest,EffectiveMultiplierTest,BoosterBigDecimalTest,HistoryMapperTest,GameSessionCacheTest,GameRateLimitFilterTest,PublicGameStateArchitectureTest

# Полная проверка
./mvnw verify
```

### Объём

- **26** test-классов в `src/test/java`.
- При запущенном Docker: **197** тест-кейсов (интеграция с PostgreSQL, API, WebSocket).
- Без Docker: **88** выполненных unit-тестов, **109** интеграционных помечены skipped.

### Ключевые модули

| Модуль | Тип тестов | Сценарии |
|--------|------------|----------|
| `ProvablyFairService` | unit | golden vectors, негативные seed, детерминизм hash |
| `CrashMathService`, `BoosterScoringTest` | unit | границы множителей, house edge |
| `StartRoundIntegrationTest`, `StateCashoutIntegrationTest` | integration | positiv/negativ API, баланс |
| `PlayerAuthIntegrationTest` | integration | register, JWT, puzzle sync |

### JaCoCo (покрытие)

После `./mvnw test` (unit-набор) и `jacoco:report`:

| Метрика | Значение |
|---------|----------|
| LINE (весь backend) | **17.7%** (320/1809) — низко из-за большого объёма Spring/JPA без integration |
| LINE **provablyfair** | **83.0%** — критичный модуль честности раунда |
| BRANCH | 13.1% |

HTML-отчёт: [`coverage/backend/index.html`](coverage/backend/index.html)  
Источник Maven: `target/site/jacoco/` (копия в `docs/module4/coverage/backend/`).

> Для учебного отчёта акцент на **критических модулях** (PF, crash math, cashout), а не на общем проценте по всему monolith.

## Frontend (Vitest)

```bash
cd frontend
npm test
npm run test:coverage
```

**Результат (2026-10-29):** **6** файлов, **33** теста, все passed.

| Область | Line coverage |
|---------|----------------|
| **Все включённые файлы** | **33.33%** lines (admin UI без UI-тестов) |
| `game/betAmount.ts` | 75.86% |
| `game/ghostFlight.ts` | 100% |
| `auth/albumSlots.ts` | 100% |
| `admin/configModel.ts` | 82.5% |
| `stores/gameStore.ts` | 84% |

HTML: [`coverage/frontend/index.html`](coverage/frontend/index.html) после `npm run test:coverage`.

Конфиг coverage: [`frontend/vite.config.ts`](../../frontend/vite.config.ts) секция `test.coverage`.
