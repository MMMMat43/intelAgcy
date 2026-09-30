# Руководство пользователя

Агент принимает Kotlin-код (`.kt`) и создаёт для него тесты на Kotlin/JUnit5.
Скрипт `run.ps1` в корне проекта скрывает детали запуска.

Что проверено, а что нет: запуск через Docker, загрузка файла в REST API,
CLI и сборка проверялись реальными запусками. Веб-форму n8n в браузере мы не
открывали: её JSON собран по документации n8n, а REST-часть, которую она
вызывает, проверена отдельно.

## 1. Что нужно

- Windows и PowerShell.
- **Docker Desktop** для запуска одной командой (рекомендуется).
- Для запуска без Docker: JDK 17 или 21. Новее нельзя: Kotlin 2.0.21 не
  работает на новых JDK (см. раздел 9).
- Git, если нужно клонировать проект.

## 2. Запуск одной командой

```powershell
.\run.ps1 up
```

Что делает команда (по коду `run.ps1`):

1. Проверяет, что Docker установлен и запущен.
2. Создаёт каталог `build\agent-output`.
3. Выполняет `docker compose up --build -d`: собирает образ агента и
   запускает его вместе с n8n.
4. До 60 секунд ждёт ответа `http://localhost:8080/health`.
5. Печатает адреса: API `http://localhost:8080/health`, n8n
   `http://localhost:5678`.

Первая сборка занимает несколько минут: скачиваются базовые образы и
зависимости Gradle. Образ агента около 832 МБ из-за встроенного компилятора
Kotlin. Повторные запуски быстрее благодаря кэшу слоёв.

Остановить: `.\run.ps1 down`. Логи:
`docker compose logs -f agent` и `docker compose logs -f n8n`.

**Не удаляйте** папку `build\agent-output`, пока контейнеры запущены. На Windows
это ломает монтирование папки внутри работающего контейнера, и запросы
начинают падать. Если это случилось, выполните `.\run.ps1 up` ещё раз.

## 3. Форма загрузки в n8n

1. Откройте `http://localhost:5678` и создайте локальную учётку (данные
   хранятся только в вашем контейнере).
2. **Workflows → Import from File**, выберите
   `integration/n8n/workflow.json`.
   Если вы импортировали этот файл раньше (версия под `.java`), **удалите
   старый workflow и импортируйте заново**: форма теперь принимает только
   `.kt`.
3. В узле **On form submission** есть **Test URL**. Проще нажать
   **Execute workflow** и выбрать файл в открывшейся форме. Постоянная
   форма (Production URL) доступна после включения переключателя
   **Active**.
4. Результат (ответ API) виден на узле **Call IntelligentTestAgent API**.
   Файлы лежат на вашем компьютере в `build\agent-output\<uuid>\`.

Если узел **Call IntelligentTestAgent API** сообщает об отсутствии файла,
проверьте в нём поле **Input Data Field Name**: оно должно совпадать с именем
бинарного свойства из вывода предыдущего узла (в файле workflow указано
`Kotlin_file`).

## 4. Загрузка файла из PowerShell

`curl.exe -F` на некоторых компьютерах зависает, поэтому используйте .NET
`HttpClient`:

```powershell
Add-Type -AssemblyName System.Net.Http
$path = "C:\путь\к\Файлу.kt"
$client = New-Object System.Net.Http.HttpClient
$client.Timeout = [TimeSpan]::FromMinutes(10)
$content = New-Object System.Net.Http.MultipartFormDataContent
$bytes = [System.IO.File]::ReadAllBytes($path)
$file = New-Object System.Net.Http.ByteArrayContent(,$bytes)
$content.Add($file, "file", [System.IO.Path]::GetFileName($path))
$response = $client.PostAsync("http://localhost:8080/generate-tests-upload", $content).Result
$response.StatusCode
$response.Content.ReadAsStringAsync().Result
```

Запятая в `ByteArrayContent(,$bytes)` обязательна. Без `outputDir` каждая
загрузка получает каталог `/data/output/<uuid>` внутри контейнера, на вашем
компьютере это `build\agent-output\<uuid>`.

Другие эндпоинты:

| Запрос | Назначение |
|---|---|
| `GET /health` | `{"status":"ok"}` |
| `POST /analyze` с `{"sourcePath": "..."}` | Структура кода без генерации тестов |
| `POST /generate-tests` с `{"sourcePath": "...", "outputDir": "..."}` | Генерация по пути, видимому **серверу** (внутри контейнера это, например, `/data/samples/OrderProcessor.kt`) |
| `POST /generate-tests-upload` | Генерация по загруженному файлу (часть `file`, необязательная часть `outputDir`) |

Примеры лежат в контейнере в `/data/samples`: `OrderProcessor.kt`,
`SampleCalculator.kt`, `SampleStringUtils.kt`.

## 5. Запуск без Docker

```powershell
.\run.ps1 generate -Source путь\к\Файлу.kt
.\run.ps1 generate -Source путь\к\Файлу.kt -Output моя_папка
.\run.ps1 analyze  -Source путь\к\Файлу.kt
.\run.ps1 serve
.\run.ps1 serve -Port 9090
.\run.ps1 build
.\run.ps1 test
```

- `generate` анализирует код, создаёт тесты и сохраняет результат в
  `build\agent-output` (или в `-Output`). В консоль печатаются тест-кейсы,
  покрытие ветвей по функциям, пропущенные функции и предупреждения.
- `analyze` печатает структуру кода (функции, ветви, сложность).
- `-Source` может быть файлом или каталогом: обрабатываются все `.kt`,
  кроме каталогов `.git`, `build`, `target`, `out`, `.gradle`.
- `serve` запускает REST API на порту 8080 (`-Port` меняет порт).

Скрипт сам ищет подходящий JDK (переменная `JAVA_HOME`, JBR из JetBrains IDE,
JDK 17 или 21). Если ничего не найдено, создайте файл `.jdkhome` в корне
проекта с одной строкой: путём к JDK 17 или 21. Файл не попадает в git.

Эквивалент без скрипта:

```powershell
.\gradlew.bat run --args="--source путь\к\Файлу.kt --generate-tests --output build\agent-output" --no-daemon
```

Флаги CLI: `--source <путь>` (обязателен), `--generate-tests`,
`--output <каталог>`.

## 6. Нейросеть (необязательно)

Без ключа всё работает на собственном алгоритме. Нейросеть нужна только для
веток, которые наш алгоритм не смог покрыть, и принимается лишь то, что
реально увеличивает покрытие (подробности в `ARCHITECTURE.md`, раздел 5).
Если все ветки покрыты, к нейросети не обращаются совсем.

Чтобы включить:

```powershell
Copy-Item .env.example .env
```

Откройте `.env` и заполните:

| Переменная | Значение по умолчанию | Смысл |
|---|---|---|
| `LLM_API_KEY` | пусто | Ключ доступа |
| `LLM_MODEL` | `gpt-4o-mini` | Имя модели |
| `LLM_API_BASE_URL` | `https://api.openai.com/v1` | Адрес API, совместимого с OpenAI (`/chat/completions`) |

Подойдёт любой провайдер с таким API. Список бесплатных моделей у
провайдеров меняется, актуальные имена смотрите в их кабинетах.

- `.env` добавлен в `.gitignore`. **Не коммитьте ключ и не публикуйте его.**
- `run.ps1` читает `.env` при локальных командах, `docker compose` читает его
  при `up`. После изменения `.env` перезапустите стек:
  `.\run.ps1 down`, затем `.\run.ps1 up`.
- Если провайдер отвечает ошибкой (например HTTP 403 или 429), шаг
  пропускается и результат получается без нейросети. Признак того, что
  нейросеть участвовала: в `test-cases.json` есть кейсы с `id`,
  содержащим `-llm-`. Если таких нет, её вклада в результате нет.

## 7. Как читать результат

В каталоге результата:

```
analysis.json
test-cases.json
coverage.json
generated-tests\GeneratedTests.kt
```

(если у исходника есть `package`, файл лежит глубже, по пути пакета.)

**Ответ API.** Поля `functionsCount`, `testCasesCount`, `executedTestCases`,
`skippedFunctions`, `warnings`, `branchCoverage`, `coveredBranches`,
`totalBranches`, `coverageMeasured`. Подробный смысл в
`ARCHITECTURE.md`, раздел 7. Главное: `branchCoverage = null` означает, что
покрытие не вычислялось (нет ветвей или код не удалось разметить), а не 0%.

**coverage.json.** `covered` это ветки, которые выполнены; `notFoundWithinBudget`
это ветки, для которых алгоритм не нашёл входов за отведённые попытки и
время (это может быть и недостижимый код); `notInstrumentable` это ветки, в
которые не удалось поставить метку.

**test-cases.json.** Описание каждого кейса для человека: что проверяется,
входные данные, ожидаемый результат. Тип `BOUNDARY`, `POSITIVE`
или `NEGATIVE`.

**GeneratedTests.kt.** Готовый код тестов. Проверки получены **запуском
вашего кода**: ожидаемое значение это то, что функция вернула при
генерации. Поэтому просмотрите файл глазами: если функция содержит ошибку,
тест закрепит её как норму. Пример: в реальном результате встречается
`assertEquals(20.200000000000003, ...)`, то есть артефакт вещественной
арифметики, сравнённый без допуска.

## 8. Как подключить тесты к своему проекту

1. Положите `GeneratedTests.kt` в `src/test/kotlin/<путь по пакету>/`. Пакет
   файла совпадает с пакетом исходного кода, поэтому импортировать тестируемый
   класс не нужно. Если у исходника нет `package`, тест тоже без пакета.
2. Подключите JUnit 5 в `build.gradle.kts`:

```kotlin
dependencies {
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
}

tasks.test {
    useJUnitPlatform()
}
```

3. Запустите `.\gradlew.bat test`.

Проверено для `OrderProcessor.kt`: сгенерированный файл, скомпилированный
вместе с исходником в отдельном модуле, дал 68 прошедших тестов из 68. Для
вашего кода это не гарантируется.

## 9. Типичные проблемы

| Симптом | Причина и что делать |
|---|---|
| В ответе `functionsCount = 0`, в `warnings` есть «синтаксическая ошибка в строке N» | Файл не разбирается. Исправьте ошибку в указанной строке |
| `functionsCount = 0`, предупреждений нет, `branchCoverage = null` | В файле нет функций. Проверьте, что загружен нужный файл |
| Функция в `skippedFunctions` | Причина написана рядом: `suspend`, generic, extension, `private`, неподдерживаемый тип параметра и т. д. Такие функции не тестируются |
| Тест содержит `// TODO` вместо проверки | Функцию не удалось выполнить (например, нет конструктора без аргументов) |
| Файл с BOM | BOM снимается автоматически, ничего делать не нужно |
| `branchCoverage` ниже 1,0 | Смотрите `notFoundWithinBudget` в `coverage.json`: там условия непокрытых веток |
| Нейросеть отвечает 403 или 429 | Проверьте ключ и лимиты у провайдера. Система продолжает работать без неё |
| `curl.exe -F` зависает | Используйте пример из раздела 4 |
| Ошибка Gradle о версии JDK | Kotlin 2.0.21 не работает на новых JDK |
| Запросы вдруг падают после удаления `build\agent-output` | Повторите `.\run.ps1 up` |
| `docker compose up` падает на шаге `installDist` с сообщением о `org.gradle.java.home` | В `gradle.properties` **проекта** не должно быть строки `org.gradle.java.home`: в контейнере нет вашего Windows-пути |

### JDK и Kotlin

Если IDE или Gradle запускаются на слишком новом JDK, выберите JDK 17 или 21
в настройках IDE (Settings → Build Tools → Gradle → Gradle JVM). Чтобы
закрепить JDK для всех ваших проектов, создайте пользовательский файл
`%USERPROFILE%\.gradle\gradle.properties` со строкой:

```
org.gradle.java.home=C:/путь/к/jdk-21
```

**Не добавляйте эту строку в `gradle.properties` самого проекта:** файл
копируется в Docker-образ, и сборка образа упадёт.
