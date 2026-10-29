# Документация проекта «Воздушный Шар»

Артефакты анализа, проектирования и требований (учебная / защитная документация).

**Сводный отчёт:** [REPORT.md](REPORT.md)  
**Отчёт по практической работе №3:** [PRACTICE_3_REPORT.md](PRACTICE_3_REPORT.md)  
**Журнал изменений релиза v0.1.0:** [../CHANGELOG.md](../CHANGELOG.md)

## Этап 1 — Предметная область и процессы

| Артефакт | Путь |
|----------|------|
| Описание As-Is / To-Be | [bpmn/README.md](bpmn/README.md) |
| Диаграмма As-Is | [bpmn/as_is.puml](bpmn/as_is.puml) → `as_is.png` |
| Диаграмма To-Be | [bpmn/to_be.puml](bpmn/to_be.puml) → `to_be.png` |
| Функциональные требования | [requirements/functional.md](requirements/functional.md) |

## Этап 2 — Архитектура и данные

| Артефакт | Путь |
|----------|------|
| C4 Context (L1) | [architecture/context_diagram.puml](architecture/context_diagram.puml) |
| C4 Container (L2) | [architecture/container_diagram.puml](architecture/container_diagram.puml) |
| ER-диаграмма | [data/er_diagram.puml](data/er_diagram.puml) |
| Нормализация и сущности | [data/README.md](data/README.md) |

## Этап 3 — NFR и ADR

| Артефакт | Путь |
|----------|------|
| Нефункциональные требования | [requirements/non_functional.md](requirements/non_functional.md) |
| ADR-001 Монолит, server-authoritative | [adr/001_monolith_server_authoritative.md](adr/001_monolith_server_authoritative.md) |
| ADR-002 PostgreSQL, STOMP + fallback | [adr/002_postgresql_and_realtime_transport.md](adr/002_postgresql_and_realtime_transport.md) |

## Экспорт диаграмм в PNG

При установленном PlantUML:

```bash
plantuml docs/bpmn/*.puml docs/architecture/*.puml docs/data/er_diagram.puml
```

## Прочее

- [expert-guide.md](expert-guide.md) — параметры конфига для жюри
- [../README.md](../README.md) — запуск, API, roadmap
