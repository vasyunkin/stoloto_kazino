# Воздушный Шар — Frontend

React-клиент к Spring backend.

| Документ | Путь |
|----------|------|
| Техспека FE | [`.specs/3-frontend-technical-spec.md`](../.specs/3-frontend-technical-spec.md) |
| Слайсы F1–F6 | [`.specs/4-frontend-implementation-slices.md`](../.specs/4-frontend-implementation-slices.md) |
| Steampunk + альбом | [`.specs/5-frontend-steampunk-visual-spec.md`](../.specs/5-frontend-steampunk-visual-spec.md) |
| Макеты | [`.specs/assets/mockups/`](../.specs/assets/mockups/) |
| Backend | [корневой README](../README.md), Swagger `:8080/swagger-ui.html` |

## Стек

| | |
|---|---|
| UI | React 19 + TypeScript + Vite |
| Canvas | PixiJS 8 (lerp K, crash burst) |
| State | Zustand |
| UI motion | Framer Motion |
| Realtime | `@stomp/stompjs` → `/ws`, stale → REST poll 200 ms |

## Запуск

### Docker (рекомендуется для демо)

Из корня репо:

```bash
docker compose up --build
```

UI: [http://localhost:3000](http://localhost:3000) (nginx + прокси `/api` `/ws` на backend).

### Локально (Vite HMR)

Нужен backend на `http://localhost:8080`:

```bash
# из корня репо
docker compose up --build postgres backend
# или только postgres + ./mvnw spring-boot:run
```

```bash
cd frontend
npm install
npm run dev
```

UI: [http://localhost:5173](http://localhost:5173). Vite проксирует `/api`, `/actuator`, `/ws`.

Опционально: `VITE_API_BASE=http://localhost:8080 npm run dev`.

## Demo path

1. Hub — авто-deposit новому `playerId`, баланс в header.
2. Выбери **Красный** (`LUCKY`) / **Зелёный** (`STANDARD`) шар.
3. Prefight — ставка, карточка бустера (`NONE`/`AUTO`), **Начать**.
4. Flight — Pixi + K; **Забрать** или дождись crash.
5. Result — win/lose + пазл (не на VOID).
6. History pills / рейтинг — без `crashPoint`/`serverSeed`.

## Скрипты

- `npm run dev` — разработка
- `npm run build` — production
- `npm run preview` — `dist/`

## Слайсы

**F1–F6 закрыты.** Дальше — ассеты/тюнинг по жюри, без смены API.
