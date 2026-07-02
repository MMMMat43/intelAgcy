# IntelligentTestAgent

Интеллектуальный агент для автоматизированного анализа Java-кода и генерации
тестов (Kotlin/JUnit5), реализованный на Kotlin/Gradle и интегрируемый с
low-code платформой [n8n](https://n8n.io/) через REST API.

## Возможности

- Статический анализ Java-кода (JavaParser): функции, параметры, ветвления,
  циклы, обработка исключений, цикломатическая сложность.
- Генерация тестовых сценариев (позитивных, негативных, граничных):
  формальные эвристики + опциональное обогащение через внешний LLM API
  (OpenAI-совместимый REST), с безопасным fallback на чистые эвристики,
  если LLM недоступен.
- Генерация исполняемого кода тестов на Kotlin/JUnit5 (KotlinPoet).
- Сохранение артефактов (анализ, тест-кейсы, сгенерированный код) на диск.
- REST API (Ktor) и CLI как две точки входа в один и тот же конвейер.
- Интеграция с n8n через готовый workflow (`integration/n8n/workflow.json`).

## Сборка и запуск

### Требования к окружению

Проект использует Kotlin `2.0.21`. На машинах, где системный `JAVA_HOME`
указывает на JDK, не поддерживаемый текущим Kotlin-компилятором (например,
JDK 26), перед запуском Gradle нужно явно указать совместимый JDK (17 или
21), например:

```powershell
$env:JAVA_HOME = "C:/Program Files/JetBrains/PyCharm 2025.2.3/jbr"
```

### Сборка и тесты

```powershell
.\gradlew.bat build --no-daemon
.\gradlew.bat test --no-daemon
```

### Запуск CLI

```powershell
# Только анализ кода
.\gradlew.bat run --args="--source path/to/YourFile.java"

# Анализ + генерация тестовых сценариев
.\gradlew.bat run --args="--source path/to/YourFile.java --generate-tests"

# Анализ + генерация тестов + сохранение всех артефактов на диск
.\gradlew.bat run --args="--source path/to/YourFile.java --generate-tests --output build/agent-output"
```

### Запуск REST API сервера

Точка входа сервера — `com.example.agent.api.ServerKt` (файл `Server.kt`),
отдельная от точки входа CLI (`com.example.agent.CliKt`). Порт задаётся
переменной окружения `PORT` (по умолчанию `8080`). Для запуска сервера
определён отдельный Gradle-таск `runServer`:

```powershell
$env:PORT = "8080"
.\gradlew.bat runServer --no-daemon
```

(либо соберите jar через `./gradlew build` и запустите
`java -cp build/libs/IntelligentTestAgent-1.0-SNAPSHOT.jar com.example.agent.api.ServerKt`).

## REST API

### `GET /health`

Проверка доступности сервиса.

```bash
curl http://localhost:8080/health
# {"status":"ok"}
```

### `POST /analyze`

Анализирует Java-исходник (файл или директорию) и возвращает структуру кода.

```bash
curl -X POST http://localhost:8080/analyze \
  -H "Content-Type: application/json" \
  -d '{"sourcePath": "/path/to/YourFile.java"}'
```

Ответ (200 OK) — JSON-представление `CodeStructure` (функции, параметры,
ветвления, циклы, исключения, цикломатическая сложность).

Если `sourcePath` не найден — ответ `400 Bad Request` с телом
`{"error": "..."}`.

### `POST /generate-tests`

Запускает полный конвейер: анализ → генерация тестовых сценариев →
(опционально) генерация Kotlin/JUnit5-кода и сохранение всех артефактов.

```bash
curl -X POST http://localhost:8080/generate-tests \
  -H "Content-Type: application/json" \
  -d '{"sourcePath": "/path/to/YourFile.java", "outputDir": "/tmp/agent-output"}'
```

Поле `outputDir` необязательно: если оно не указано, тесты генерируются
"в памяти" и сохранение на диск не выполняется (сервис вернёт краткую
сводку). Если `outputDir` указан, в нём появятся `analysis.json`,
`test-cases.json` и Kotlin-файл с тестами в `generated-tests/`.

Ответ (200 OK):

```json
{
  "functionsCount": 3,
  "testCasesCount": 12,
  "generatedTestFilePath": "/tmp/agent-output/generated-tests"
}
```

## Интеграция с n8n

В каталоге `integration/n8n/workflow.json` находится готовый экспорт
workflow для [n8n](https://n8n.io/) с HTTP-нодой, вызывающей
`POST /generate-tests` этого API.

### Импорт workflow

1. Откройте n8n (self-hosted или n8n cloud).
2. В верхнем меню выберите **Workflows → Import from File**.
3. Укажите файл `integration/n8n/workflow.json` из этого репозитория.
4. При необходимости отредактируйте ноду **Call IntelligentTestAgent API**:
   - измените URL, если сервер запущен не на `http://localhost:8080`;
   - настройте `sourcePath`/`outputDir` под свой сценарий (через входные
     данные триггера или прямо в теле запроса JSON-ноды).
5. Запустите workflow вручную (**Manual Trigger**) или замените триггер на
   Webhook/расписание под свои нужды.

Postoянно работающий инстанс n8n не входит в состав этого репозитория —
предполагается, что пользователь запускает свой собственный n8n (например,
через `npx n8n` или Docker) и импортирует workflow в него.
