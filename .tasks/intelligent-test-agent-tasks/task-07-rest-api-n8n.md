# Task 07: Ktor REST API + интеграция с n8n

**Type:** Code Modification
**Suggested agent:** Code

## Goal
Реализовать REST API на Ktor (Netty), объединяющий весь конвейер (загрузка исходников → анализ → генерация сценариев → генерация тестов → сохранение артефактов) в HTTP-эндпоинты, и подготовить workflow-файл для импорта в n8n, демонстрирующий вызов этого API из low-code платформы.

## Why This Task Exists
Раздел 2.2 ПЗ описывает "Модуль взаимодействия с пользователем", реализуемый через low-code платформу (n8n), которая визуально настраивает процессы и управляет работой агента. REST API — это граница интеграции между собственным Kotlin-бэкендом и n8n; без него low-code платформа не может вызвать пайплайн.

## Spec Coverage
- Requirements: R4
- Scenarios: S2, S3

## Required Inputs
- Все предыдущие модули: `com.example.agent.source.SourceLoader` (Task 02), `com.example.agent.analysis.JavaCodeAnalyzer` (Task 03), `com.example.agent.generation.TestScenarioGenerator` (Task 05), `com.example.agent.codegen.JUnit5TestCodeGenerator` и `com.example.agent.storage.ArtifactStorage` (Task 06).
- Уже объявленные зависимости: `io.ktor:ktor-server-netty:2.3.6`, `io.ktor:ktor-server-content-negotiation:2.3.6`, `io.ktor:ktor-serialization-jackson:2.3.6`.
- n8n — low-code платформа для оркестрации workflow через HTTP-ноды (Webhook / HTTP Request); в этой задаче n8n не поднимается как сервис — только создаётся workflow JSON-файл, импортируемый пользователем в свой инстанс n8n вручную.

## Files/Areas
- `src/main/kotlin/com/example/agent/api/` — новый пакет: `Server.kt` (настройка Ktor `embeddedServer`), `Routes.kt` (маршруты), `dto/` (DTO для запросов/ответов, если нужно отделить от доменной модели).
- `src/test/kotlin/com/example/agent/api/` — тесты маршрутов через Ktor `testApplication` (не требует реального сетевого порта).
- `integration/n8n/workflow.json` (новая директория в корне проекта) — экспортируемый n8n workflow.
- `README.md` (корень проекта, создать если отсутствует, либо дополнить) — раздел "Как запустить" + "Интеграция с n8n".
- `docker-compose.yml` (опционально, корень проекта) — для локального подъёма n8n рядом с приложением, если посчитаешь нужным; не обязателен для выполнения acceptance criteria.

## Constraints / Non-Goals
- Не требуется постоянно работающий hosted n8n — только предоставленный workflow JSON и инструкция по импорту.
- Эндпоинты должны принимать JSON и возвращать JSON (используя `ktor-serialization-jackson`), с понятными кодами ошибок (400 для некорректного пути, 500 для внутренних ошибок с сообщением, без падения процесса).
- `CliKt.main` (Task 01) должен оставаться рабочим отдельно от REST-сервера — не заменять CLI сервером, а дать возможность запускать оба режима (например, через отдельный аргумент `--serve` в `Cli.kt`, который запускает Ktor-сервер, либо отдельный `ServerMain.kt`; выбери и задокументируй один подход).

## Output Artifacts
- `integration/n8n/workflow.json` — n8n workflow с HTTP-нодой, вызывающей `/generate-tests` нашего API.
- `README.md` — обновлённый/новый раздел с инструкцией запуска сервера и импорта workflow в n8n.

## What to Do
- Реализовать минимум два эндпоинта:
  - `POST /analyze` — принимает `{ "sourcePath": string }`, вызывает `SourceLoader` + `JavaCodeAnalyzer`, возвращает JSON `CodeStructure`.
  - `POST /generate-tests` — принимает `{ "sourcePath": string, "outputDir": string }`, прогоняет полный пайплайн (загрузка → анализ → генерация сценариев → генерация кода → сохранение через `ArtifactStorage`), возвращает JSON с кратким summary (например, количество найденных функций, количество сгенерированных тест-кейсов, путь к сгенерированному `.kt`-файлу).
  - `GET /health` — простой health-check, возвращает `{ "status": "ok" }`.
- Реализовать точку запуска сервера (`fun main` в `Server.kt` либо через флаг в `Cli.kt`), слушающую порт из переменной окружения `PORT` (по умолчанию 8080).
- Написать тесты маршрутов с Ktor `testApplication` (или `TestApplicationEngine` в зависимости от версии Ktor 2.3.6 API):
  1. `GET /health` возвращает 200 и `{"status":"ok"}`.
  2. `POST /analyze` с валидным `sourcePath` (Java-файл, подготовленный в `@TempDir` теста) возвращает 200 и корректную структуру.
  3. `POST /analyze` с несуществующим путём возвращает 400 с телом ошибки (не 500 и не необработанное исключение).
- Создать `integration/n8n/workflow.json`: минимальный валидный n8n workflow с нодой типа `n8n-nodes-base.httpRequest` (метод POST, URL `http://localhost:8080/generate-tests`, JSON body с плейсхолдерами `sourcePath`/`outputDir`), опционально с нодой-триггером (`Manual Trigger` или `Webhook`). Формат должен быть валидным n8n export JSON (поля `name`, `nodes`, `connections`, `active`, `settings` — используй актуальную известную структуру n8n workflow export).
- Обновить/создать `README.md` с разделами: "Сборка и запуск" (`./gradlew build`, запуск сервера), "REST API" (описание трёх эндпоинтов с примерами curl), "Интеграция с n8n" (как импортировать `integration/n8n/workflow.json` в n8n UI: Workflows → Import from File, и как настроить URL при необходимости).

## Expected Output
- Модуль `com.example.agent.api` с тремя эндпоинтами и тестами.
- Файл `integration/n8n/workflow.json`.
- Обновлённый `README.md`.

## Acceptance Criteria
- [ ] `POST /analyze`, `POST /generate-tests`, `GET /health` реализованы и покрыты тестами через Ktor test-механизм.
- [ ] Ошибочные входные данные (несуществующий путь) обрабатываются с кодом 400, без падения процесса.
- [ ] `integration/n8n/workflow.json` — валидный JSON с корректной n8n-структурой (`nodes`, `connections`) и HTTP-нодой, указывающей на `/generate-tests` нашего API.
- [ ] `README.md` содержит инструкцию по запуску сервера и импорту workflow в n8n.
- [ ] `./gradlew build` и `./gradlew test` проходят успешно.
- [ ] Covered requirements and scenarios are satisfied (R4, S2, S3).
- [ ] I've created a git commit for this task.
