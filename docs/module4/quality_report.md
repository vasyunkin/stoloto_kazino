# Отчёт по качеству кода — модуль 4

Дата прогона: **2026-10-29**. Проект: balloon-game.

## Линтер frontend (Oxlint)

Команда:

```bash
cd frontend && npm run lint
```

Конфиг: [`frontend/oxlint.json`](../../frontend/oxlint.json).

**Результат:** ошибок уровня `error` нет; **8 предупреждений** `react(set-state-in-effect)` в экранах Hub, Prefight, Flight и хуках; отдельные `react-in-jsx-scope` в legacy-участках Pixi.

Полный лог: [`logs/oxlint.log`](logs/oxlint.log).

**Действия:** предупреждения зафиксированы как технический долг; критичные пути (ставки, PF, API) не блокируются линтером.

## Статический анализ Java (Checkstyle)

Команда:

```bash
./mvnw checkstyle:checkstyle
```

Конфиг: [`checkstyle.xml`](../../checkstyle.xml). Режим: `failsOnError=false`, severity **warning**.

**Результат:** замечания по `WhitespaceAround` в record/DTO, `NeedBraces` в `CrashMathService` / `ScoringService`, один **UnusedImports** в `PlayerAuthService` (исправлен в рамках модуля 4).

Лог: [`logs/checkstyle-console.log`](logs/checkstyle-console.log).

## SonarLint / IDE

Дополнительно использовался **SonarLint** в IDE: подсветка потенциальных NPE, дублирования строк, сложности методов. Критичных уязвимостей в игровом API не выявлено; рекомендации сведены к стилю и мелкому рефакторингу.

## Сводка

| Инструмент | Область | Итог |
|------------|---------|------|
| Oxlint | frontend | 0 errors, warnings задокументированы |
| Checkstyle | backend Java | warnings, 1 import исправлен |
| EditorConfig | весь репозиторий | единые отступы и кодировка |
| pre-commit | git commit | lint + unit-тесты на staged frontend/java |
