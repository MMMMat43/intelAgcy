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

## Быстрый старт (рекомендуется)

В корне проекта есть скрипт `run.ps1`, который сам находит совместимый JDK
на вашей машине (или отдельно установленный JDK 17/21, или JBR из
JetBrains-IDE) и подставляет нужные флаги Gradle — вам не нужно ничего
выставлять руками.

```powershell
# Показать список команд
.\run.ps1

# Собрать проект и прогнать тесты
.\run.ps1 build
.\run.ps1 test

# Проанализировать Java-файл
.\run.ps1 analyze -Source path\to\YourFile.java

# Проанализировать + сгенерировать тесты (артефакты в build\agent-output)
.\run.ps1 generate -Source path\to\YourFile.java

# То же самое, но в свою папку
.\run.ps1 generate -Source path\to\YourFile.java -Output my_folder

# Запустить REST API (для n8n), порт по умолчанию 8080
.\run.ps1 serve
.\run.ps1 serve -Port 9090
```

Если `run.ps1` не может найти совместимый JDK автоматически — он подскажет,
что сделать: либо поставить JDK 17/21, либо создать файл `.jdkhome` в корне
проекта с одной строкой — путём к нужному JDK.

### Нейросеть для генерации тестов

**Важно:** этот раздел описывает поведение при локальном запуске через `.\run.ps1 serve`/`generate`
(без Docker). При запуске через `docker compose up`/`.\run.ps1 up` нейросеть
уже включена автоматически (см. раздел ниже).

При локальном запуске без Docker генерация тестов по умолчанию работает **без**
обращения к LLM — только на эвристиках (это полностью рабочий режим). Чтобы
включить настоящие вызовы к платному OpenAI, скопируйте `.env.example` в `.env` и
впишите свой ключ:

```powershell
Copy-Item .env.example .env
# затем откройте .env и впишите LLM_API_KEY=sk-...
```

`run.ps1` автоматически подхватывает `.env` перед каждой командой. Файл
`.env` в git не попадает (добавлен в `.gitignore`).

#### В Docker (рекомендуется): бесплатная локальная модель через Ollama, без ключа

При запуске через `docker compose up --build -d` (или `.\run.ps1 up`) в стеке
автоматически поднимается **бесплатная локальная модель** через
[Ollama](https://ollama.com/) (модель `llama3.2:1b`) — никакой регистрации или
платного ключа не нужно — вызовы к LLM работают сразу же "из коробки".

При первом запуске отдельный сервис `ollama-pull` скачивает модель (~1.3 GB) в
том `ollama_data` — это может занять несколько минут в зависимости от скорости
интернета. Проверить готовность:

```powershell
docker compose logs ollama-pull
```

При повторных запусках модель уже в кэше — скачивание не повторяется. При
генерации тестов часть сценариев будет дополнена настоящими LLM-предложениями —
такие тест-кейсы легко узнать в `test-cases.json` по `id`, содержащему `-llm-`.

Хотите вместо этого использовать платный OpenAI (или другой OpenAI-совместимый
сервис)? Создайте `.env` и переопределите там `LLM_API_KEY`/`LLM_MODEL`/
`LLM_API_BASE_URL` — эти значения имеют приоритет над дефолтным Ollama.

## Сборка и запуск (вручную, без run.ps1)

Этот раздел — для тех случаев, когда `run.ps1` недоступен (например, не
Windows) или нужен более тонкий контроль над Gradle.

### Требования к окружению

Проект использует Kotlin `2.0.21`. На машинах, где системный `JAVA_HOME`
указывает на JDK, не поддерживаемый текущим Kotlin-компилятором (например,
JDK 22+), перед запуском Gradle нужно явно указать совместимый JDK (17 или
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

Переменные `LLM_API_KEY`/`LLM_MODEL`/`LLM_API_BASE_URL` (для реального
вызова LLM) при ручном запуске нужно выставлять самостоятельно перед
вызовом `gradlew` — при использовании `run.ps1` это делается автоматически
из файла `.env` (см. раздел выше).

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

## Интеграция с n8n (визуальный low-code интерфейс) — ОДНА КОМАНДА

Весь стек (наш Kotlin REST API сервер + n8n) полностью докеризован и
поднимается ОДНОЙ командой — не нужно отдельно держать открытым окно с
`.\run.ps1 serve`, сервер сам работает внутри Docker вместе с n8n.

(Обычная установка n8n через `npm install -g n8n` на момент написания
этого README ломалась из-за внутренних конфликтов версий в самом пакете
n8n — это известная причина, почему n8n сам рекомендует именно Docker.)

### Шаг 1. Установите Docker Desktop (один раз)

Скачайте и установите [Docker Desktop для Windows](https://www.docker.com/products/docker-desktop/),
перезагрузите компьютер, если установщик это попросит, и убедитесь, что
Docker Desktop запущен (иконка кита в трее).

### Шаг 2. Поднимите весь стек одной командой

```powershell
.\run.ps1 up
```

Это выполнит `docker compose up --build -d`: соберёт образ нашего агента
(Dockerfile в корне репозитория) и запустит его вместе с n8n в общей сети.
Первый запуск может занять несколько минут (скачиваются базовые образы
JDK/JRE и зависимости Gradle внутри контейнера сборки) — повторные запуски
значительно быстрее за счёт кэша слоёв Docker. Скрипт сам дождётся, пока
агент ответит на `/health`, и напечатает готовые адреса.

После этого откройте в браузере **http://localhost:5678** — это и есть
визуальный интерфейс n8n. При первом заходе он попросит создать локальную
учётку (email + пароль, хранятся только локально в контейнере).

### Шаг 3. Импортируйте готовый workflow

1. В n8n нажмите **Workflows → Import from File**.
2. Выберите файл `integration/n8n/workflow.json` из этого репозитория.
3. В импортированном workflow будут две ноды: **Manual Trigger** и **Call
   IntelligentTestAgent API** (HTTP-запрос к `http://agent:8080/generate-tests`
   — `agent` это имя нашего сервиса внутри общей Docker-сети — с готовым
   путём к `SampleCalculator.java`, смонтированному внутрь контейнера
   агента — менять ничего не нужно для первого запуска).
4. Нажмите **Execute workflow** — n8n визуально покажет, как запрос ушёл в
   наш API, и вернётся ответ (количество функций/тест-кейсов, путь к файлам).
5. Чтобы проанализировать свой файл, откройте ноду **Call IntelligentTestAgent
   API** и измените `sourcePath` в теле запроса на путь внутри контейнера
   агента, например `/data/samples/SampleStringUtils.java` (папка
   `src/test/resources` этого репозитория смонтирована туда как
   `/data/samples`, только для чтения).
6. Сгенерированные артефакты (`analysis.json`, `test-cases.json`,
   Kotlin-файл с тестами) появятся на хосте в `build/agent-output/` —
   том `/data/output` контейнера агента смонтирован именно туда.

> ⚠️ **Важно:** не удаляйте вручную папку `build/agent-output/`, пока стек
> запущен (`docker compose up`/`.\run.ps1 up`). На Windows это ломает
> Docker-том (bind mount) внутри уже работающего контейнера и вызывает
> ошибки вроде "The service was not able to process your request" на всех
> последующих запросах к `/generate-tests` (даже если тело запроса
> полностью корректно). Если это всё же случилось — просто перезапустите
> `.\run.ps1 up` (он автоматически воссоздаёт папку перед запуском) либо
> выполните `docker compose restart agent` после создания папки заново.

### Логи и остановка

```powershell
# Посмотреть логи
docker compose logs -f agent
docker compose logs -f n8n

# Остановить весь стек
.\run.ps1 down
```
