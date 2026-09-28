# Архитектура (C4)

| Уровень | Файл | Описание |
|---------|------|----------|
| Context (L1) | [context_diagram.puml](context_diagram.puml) | ИС как «чёрный ящик», игрок, оператор, внешние системы |
| Container (L2) | [container_diagram.puml](container_diagram.puml) | Web UI, nginx, Backend, PostgreSQL, in-memory полёты |

Экспорт PNG:

```bash
plantuml docs/architecture/context_diagram.puml docs/architecture/container_diagram.puml
```

Подробнее о потоках и API: [README.md](../../README.md#4-архитектура), [expert-guide.md](../expert-guide.md).
