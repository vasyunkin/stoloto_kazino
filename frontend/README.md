# Воздушный Шар — Frontend

React-клиент к Spring backend. Спека: [`.specs/3-frontend-technical-spec.md`](../.specs/3-frontend-technical-spec.md), слайсы: [`.specs/4-frontend-implementation-slices.md`](../.specs/4-frontend-implementation-slices.md).

## Стек

| | |
|---|---|
| UI | React 19 + TypeScript + Vite |
| Canvas | PixiJS 8 |
| State | Zustand |
| UI motion | Framer Motion |
| Realtime | `@stomp/stompjs` → `/ws` (fallback REST poll) |

## Запуск

Нужен backend на `http://localhost:8080` (`docker compose up` из корня репо).

```bash
cd frontend
npm install
npm run dev
```

Откроется `http://localhost:5173`. Vite проксирует `/api`, `/actuator`, `/ws` на `:8080`.

Опционально прямой API без proxy: `VITE_API_BASE=http://localhost:8080 npm run dev` (тогда нужен CORS — уже есть для 5173).

## Скрипты

- `npm run dev` — разработка
- `npm run build` — production bundle
- `npm run preview` — раздача `dist/`

## Текущий слайс

**F5** закрыт (cashout + result/puzzle). Дальше **F6** — history strip, polish.
