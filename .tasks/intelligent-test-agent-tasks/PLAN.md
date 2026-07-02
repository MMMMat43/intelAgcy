# Интеллектуальный агент анализа кода и генерации тестов — Execution Plan

## Goal
Реализовать в проекте `IntelligentTestAgent` (Kotlin/Gradle, package `com.example.agent`) рабочий прототип интеллектуального агента: анализ Java-кода (JavaParser), генерация тестовых сценариев (эвристики + внешний LLM по HTTP), генерация исполняемого JUnit5-кода тестов (KotlinPoet) и REST API (Ktor) как точка интеграции с low-code платформой n8n. Дополнительно — привести текст пояснительной записки (ПЗ) курсового проекта в соответствие с фактической реализацией (Kotlin/JUnit5 вместо Python/pytest) и усилить раздел новизны сравнением с аналогами.

## Scope
- In scope: доменная модель; загрузка исходников (локальный путь/git); статический анализ Java-кода через JavaParser (функции, параметры, ветвления, циклы, исключения, цикломатическая сложность); HTTP-клиент внешнего LLM с fallback на чистые эвристики; генерация структурированных тест-кейсов (JSON, Jackson); генерация JUnit5-тестов на Kotlin через KotlinPoet; REST API на Ktor; workflow-файл для n8n; сквозная проверка на примере проекта; обновление текста ПЗ (docx).
- Out of scope: анализ кода на языках, отличных от Java; постоянно работающий hosted-инстанс n8n (только workflow JSON + инструкция, опционально docker-compose); дообучение/хостинг собственной LLM; полноценный UI помимо n8n; гарантия правки именно защищённого экземпляра ПЗ (правится копия).

## Specification

### Requirements
- R1. CLI/REST принимает путь к Java-исходнику (файл или директория) и строит структурную модель: функции, параметры, ветвления, циклы, исключения, цикломатическая сложность.
- R2. Система генерирует тестовые сценарии (позитивные/негативные/граничные), комбинируя эвристики и вызовы LLM, в виде структурированного JSON.
- R3. Система генерирует компилируемый исходный код JUnit5-тестов на Kotlin (через KotlinPoet) из структурированных тест-кейсов.
- R4. REST API (Ktor) предоставляет эндпоинты анализа и генерации тестов, пригодные для вызова из workflow n8n (HTTP Request/Webhook ноды).
- R5. Артефакты (результат анализа, JSON тест-кейсов, сгенерированный код тестов) сохраняются на диск в структурированном виде.
- R6. Проект собирается и проходит собственный тестовый набор через Gradle (`./gradlew build`, `./gradlew test`).
- R7. Текст ПЗ обновлён: примеры технологий/тестов заменены с Python/pytest на Kotlin/JUnit5 (кроме описания стороннего решения AutoGen ITA, которое не меняется), раздел новизны расширен утверждением превосходства системы над аналогами из таблицы 1.1.

### Non-Goals
- NG1. Анализ кода только для Java, другие языки не поддерживаются.
- NG2. Постоянно работающий hosted-инстанс n8n не поставляется — только workflow-описание (JSON) и REST-эндпоинты для интеграции, опционально docker-compose для локального запуска.
- NG3. Обучение/дообучение LLM не входит в scope — только интеграция по HTTP с внешним провайдером, с обязательным fallback без него.
- NG4. Изменение оригинального защищённого файла ПЗ не гарантируется — правится копия; пользователь сам переносит правки в рабочий/защищённый экземпляр.

### Acceptance Scenarios
- S1. Запуск CLI на примере Java-файла с ветвлениями даёт JSON-отчёт анализа с функциями, ветвлениями и цикломатической сложностью.
- S2. POST на `/generate-tests` возвращает структурированные тест-кейсы и создаёт компилируемый Kotlin JUnit5-файл в выходной директории.
- S3. Предоставленный n8n workflow JSON синтаксически корректен и содержит HTTP-ноду, обращающуюся к REST API проекта.
- S4. `./gradlew build` и `./gradlew test` успешно проходят с новыми модулями и их unit-тестами.
- S5. Обновлённая копия ПЗ не содержит упоминаний pytest/Python в контексте нашей собственной системы (кроме описания аналога AutoGen ITA), содержит формулировки про Kotlin/JUnit5 и абзац о превосходстве над аналогами.

## How to Use This Plan
1. Open the next unchecked task from the checklist below.
2. Read the corresponding task file completely.
3. Use the suggested agent and the provided inputs for that task.
4. Execute only the next unchecked task unless the user changes the plan.
5. Verify all acceptance criteria, including the git commit requirement.
6. Update the checklist after the task is completed.
7. If the plan becomes stale, update the relevant files before continuing.

## Task Checklist
- [x] `task-01-project-skeleton.md`: Доменная модель и скелет проекта — Suggested agent: Code — Covers: R1, R6
- [x] `task-02-source-integration.md`: Модуль интеграции с исходниками — Suggested agent: Code — Covers: R1
- [x] `task-03-code-analysis.md`: Анализ Java-кода (JavaParser) — Suggested agent: Code — Covers: R1, S1
- [x] `task-04-llm-client.md`: LLM-клиент (OkHttp) — Suggested agent: Code — Covers: R2
- [x] `task-05-test-scenario-generation.md`: Генерация тестовых сценариев — Suggested agent: Code — Covers: R2
- [x] `task-06-test-codegen-storage.md`: Генерация JUnit5-кода и хранение артефактов — Suggested agent: Code — Covers: R3, R5
- [ ] `task-07-rest-api-n8n.md`: Ktor REST API + интеграция с n8n — Suggested agent: Code — Covers: R4, S3
- [ ] `task-08-e2e-verification.md`: Сквозная проверка и метрики — Suggested agent: Test — Covers: R6, S1, S2, S4
- [x] `task-09-pz-document-update.md`: Обновление текста ПЗ (Kotlin/JUnit5 + новизна) — Suggested agent: Code — Covers: R7, S5

## Shared Context

### Key Decisions
- Технологический стек уже зафиксирован в `build.gradle.kts` проекта: JavaParser (анализ Java), KotlinPoet (генерация Kotlin), OkHttp (HTTP-клиент LLM), Jackson (JSON), Ktor Netty server (REST API), Logback (логирование), JUnit5/kotlin-test (тесты). Главный класс объявлен как `com.example.agent.CliKt`, group проекта — `com.example`. Текущий `src/main/kotlin/Main.kt` — заглушка в пакете `org.example`, подлежит замене.
- Анализируемый язык — Java (через `com.github.javaparser:javaparser-core`), генерируемые тесты — Kotlin/JUnit5 (через `com.squareup:kotlinpoet`). Это отличается от примеров в тексте ПЗ (там фигурирует Python/pytest) — текст ПЗ приводится в соответствие в Task 09.
- LLM-клиент должен быть общим HTTP-клиентом (OpenAI-совместимый REST по умолчанию, base URL/модель/ключ — через переменные окружения), с обязательным fallback-режимом на чистые эвристики, если ключ/сеть недоступны — это нужно, чтобы `./gradlew build`/`test` оставались зелёными без реального API-ключа.
- Интеграция с n8n реализуется как REST API + предоставленный workflow JSON (нода HTTP Request на наш эндпоинт), без требования постоянно поднятого инстанса n8n для приёмки задач.
- Пояснительная записка (ПЗ) находится вне репозитория проекта: `C:/Users/motok/OneDrive/Рабочий стол/ПЗ КП.docx`. Файл может быть открыт в MS Word (наблюдалась блокировка на чтение при прямом доступе) — работать с копией.

### Constraints
- Все Kotlin-модули размещаются в пакете `com.example.agent` (и подпакетах), чтобы соответствовать `mainClass = "com.example.agent.CliKt"` в `build.gradle.kts`.
- Каждая задача с кодом обязана завершаться зелёной сборкой: `./gradlew build` (или как минимум `./gradlew compileKotlin compileTestKotlin`) и, где применимо, `./gradlew test`.
- Не добавлять новые внешние зависимости без необходимости — использовать уже объявленные в `build.gradle.kts` библиотеки. Если обнаружится нехватка (например, JGit для git-интеграции в Task 02), это нужно явно обосновать и добавить точечно.
- Секреты (LLM API ключи) не хардкодить в код — только через переменные окружения/конфиг.
- **Окружение сборки (выявлено в Task 01):** системный `JAVA_HOME`/JDK по умолчанию на этой машине — JDK 26 (`C:/Program Files/Java/jdk-26`), с которым Kotlin-компилятор падает с `IllegalArgumentException: 26` (`JavaVersion.parse` в Kotlin не знает о такой версии). Кроме того, исходный `kotlin("jvm")` плагин версии 1.9.0 несовместим с уже зафиксированным Gradle wrapper 9.6.0 (`org.gradle.api.internal.HasConvention` удалён в Gradle 9). В Task 01 плагин повышен до `2.0.21`, в `build.gradle.kts` явно зафиксирован Kotlin/Java compile target = 17. Для реального запуска `./gradlew` нужен JDK, совместимый с Kotlin-компилятором (подтверждено локально с JBR 21.0.8 из `C:/Program Files/JetBrains/PyCharm 2025.2.3/jbr`), задаваемый через `$env:JAVA_HOME` перед вызовом `gradlew.bat`, либо через закомментированную строку `org.gradle.java.home` в `gradle.properties`. **Все последующие задачи (02-09) должны учитывать это при запуске `./gradlew`.**

### Risks / Open Questions
- Нет предоставленного реального LLM API-ключа — модуль LLM и сквозная проверка должны корректно работать в fallback-режиме (без сети/ключа) для объективной проверки acceptance criteria.
- Реальный live-прогон через n8n не входит в проверяемые критерии — только структурная валидность workflow JSON и корректность REST-эндпоинтов, которые он вызывает.
- Оригинальный файл ПЗ может быть заблокирован (открыт в Word) — Task 09 работает с копией и извлечённым XML, финальный результат — обновлённая копия `.docx`, которую пользователь сам должен подставить взамен оригинала после закрытия файла в Word.

## Research Artifacts
- `.tasks/pz_kp_text.txt` — извлечённый читаемый текст пояснительной записки (все разделы, включая упоминания pytest и раздел 1.6 "Анализ существующих решений" с таблицей сравнения аналогов) — вход для Task 09.
- `.tasks/pz_kp_document.xml` — сырой `word/document.xml` из ПЗ (для точечных правок текста с сохранением форматирования) — вход для Task 09.
- `.tasks/pz_kp_copy.docx` — рабочая копия исходного `.docx` файла ПЗ (оригинал может быть заблокирован Word) — вход для Task 09.
