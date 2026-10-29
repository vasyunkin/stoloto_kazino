# Настройка среды разработки — модуль 4

Проект: **«Воздушный Шар»** (monorepo: Java backend + React frontend).

## Стек

| Часть | Технологии |
|-------|------------|
| Backend | Java **21**, Spring Boot **3.4**, Maven, PostgreSQL, Flyway |
| Frontend | **React 19**, TypeScript, Vite **8**, PixiJS, Vitest, Oxlint |
| Инфраструктура | Docker Compose, Git |

## IDE

**Основная IDE:** JetBrains **IntelliJ IDEA** (или Cursor на базе VS Code).

Рекомендуемые плагины:

- **IDEA:** Spring Boot, Lombok, EditorConfig, Checkstyle-IDEA, SonarLint, Docker.
- **VS Code / Cursor:** Extension Pack for Java, ESLint/Oxlint, EditorConfig, Docker.

## Единообразие редактора

- Корневой [`.editorconfig`](../../.editorconfig): UTF-8, LF, отступы tab для Java, 2 пробела для TS/TSX.
- Java: стиль через [Checkstyle](../../checkstyle.xml) (`mvn checkstyle:checkstyle`).
- Frontend: [Oxlint](../../frontend/oxlint.json), команда `npm run lint` в каталоге `frontend/`.

## Git-хуки

[`.pre-commit-config.yaml`](../../.pre-commit-config.yaml):

```bash
pip install pre-commit
pre-commit install
pre-commit run --all-files
```

Перед коммитом: trailing whitespace, oxlint, vitest (frontend), выборочные Java unit-тесты.

## Скриншоты IDE

Для отчёта сохраните в `docs/module4/screenshots/`:

1. Окно IDEA/Cursor с открытым проектом и включённым EditorConfig.
2. Панель Maven / терминал с `./mvnw test`.
3. Терминал `frontend` с `npm run lint` и `npm test`.
