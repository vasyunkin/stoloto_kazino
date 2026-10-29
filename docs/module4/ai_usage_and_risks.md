# ИИ-ассистенты: использование и риски — модуль 4

Инструмент: **Cursor Agent** (LLM-ассистент в IDE), аналог Copilot / GigaCode по сценарию использования.

## 1. Генерация тестов (промпт)

**Задача:** дополнить негативные сценарии для `ProvablyFairService`.

**Промпт (фрагмент):**

> Напиши JUnit 5 тесты для ProvablyFairService: проверь commitHash для фиксированного serverSeed и clientSeed; отдельный тест — пустой serverSeed должен давать IllegalArgumentException; expected hash считай через JDK MessageDigest, не вызывай сервис дважды для «ожидания».

**Результат:** расширение [`ProvablyFairServiceTest.java`](../../src/test/java/com/stoloto/balloongame/ProvablyFairServiceTest.java) — golden vectors, независимый пересчёт SHA-256, комментарии к алгоритму crash-v1.

**Ручная верификация:** прогон `./mvnw test -Dtest=ProvablyFairServiceTest` — green. Проверено, что тест не тавтологичен (ожидание через `MessageDigest`, не через `pf.hash()`).

## 2. Рефакторинг / документирование

**Промпт:**

> Убери неиспользуемый import в PlayerAuthService и объясни в одном абзаце для отчёта, зачем Checkstyle ловит UnusedImports.

**До:**

```java
import java.time.Instant;
// ... в теле класса Instant не использовался
```

**После:** import удалён; сборка и Checkstyle без предупреждения по этому файлу.

## 3. Галлюцинации и ошибки, с которыми столкнулись

| Проблема | Пример | Как обнаружили |
|----------|--------|----------------|
| Неверный API | Предложен `git push --force` в защищённую main | Сверка с Branch Protection |
| Неверные импорты | `import org.junit.Test` вместо JUnit 5 | Компиляция `mvn test` |
| «Успешный» JaCoCo без данных | argLine Surefire перезаписывал agent | Пустой `jacoco.exec`; исправлено `@{argLine}` в pom.xml |
| Избыточный тест | Дублирование вызова сервиса в expected/actual | Ревью теста вручную |
| Неточный YAML merge | Слияние конфигов с потерей отступов | Запуск Spring Boot после merge |

## 4. Риски использования ИИ

1. **Конфиденциальность:** фрагменты бизнес-логики и конфигов попадают в облако модели — для prod-секретов (JWT, admin keys) использовать только локальные заглушки.
2. **Безопасность:** ИИ может предложить ослабить валидацию или логировать seed — обязателен security review и тесты.
3. **Качество:** сгенерированный код компилируется, но не гарантирует корректность PF и денежных операций — нужны golden tests и integration tests.
4. **Ложное чувство готовности:** «все тесты зелёные» при skipped Testcontainers без Docker — проверять `mvn verify` с запущенным Docker.
5. **Стиль команды:** массовое форматирование без согласования ломает diff в PR — форматтер и линтер в pre-commit, не разовые правки ИИ.

## 5. Вывод

ИИ ускоряет написание тестов, документации module4 и конфигов (EditorConfig, pre-commit), но **не заменяет** прогон линтеров, Checkstyle, JaCoCo/Vitest и ручную верификацию критичной логики crash-игры.
