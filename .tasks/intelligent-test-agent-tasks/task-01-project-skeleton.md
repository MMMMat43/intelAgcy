# Task 01: Доменная модель и скелет проекта

**Type:** Code Modification
**Suggested agent:** Code

## Goal
Создать пакет `com.example.agent` с базовой доменной моделью (data-классы для результатов анализа кода и тест-кейсов) и рабочий CLI-скелет, соответствующий уже объявленному `mainClass = "com.example.agent.CliKt"` в `build.gradle.kts`.

## Why This Task Exists
Все последующие модули (анализ кода, LLM-клиент, генерация сценариев, генерация тестов, REST API) должны опираться на единые, заранее согласованные структуры данных. Без этого шага каждый последующий агент будет придумывать свои несовместимые модели.

## Spec Coverage
- Requirements: R1, R6
- Scenarios: (база для всех последующих)

## Required Inputs
- Текущий `build.gradle.kts` (корень проекта) уже содержит зависимости: `com.github.javaparser:javaparser-core:3.25.10`, `com.squareup:kotlinpoet:1.16.0`, `com.squareup.okhttp3:okhttp:4.12.0`, `com.fasterxml.jackson.module:jackson-module-kotlin:2.15.3`, `io.ktor:ktor-server-netty:2.3.6`, `io.ktor:ktor-server-content-negotiation:2.3.6`, `io.ktor:ktor-serialization-jackson:2.3.6`, `ch.qos.logback:logback-classic:1.4.14`, тесты — `kotlin("test")` и `org.junit.jupiter:junit-jupiter:5.10.0`. `application { mainClass.set("com.example.agent.CliKt") }`. `kotlin { jvmToolchain(17) }`.
- Текущий `src/main/kotlin/Main.kt` — заглушка "Hello World" в пакете `org.example` (16 строк). Подлежит удалению/замене.
- `settings.gradle.kts` содержит только `rootProject.name = "IntelligentTestAgent"`.

## Files/Areas
- `src/main/kotlin/Main.kt` — удалить или заменить.
- `src/main/kotlin/com/example/agent/` — новая корневая директория пакета.
- `src/main/kotlin/com/example/agent/model/` — доменная модель.
- `src/main/kotlin/com/example/agent/Cli.kt` — новая точка входа (файл `CliKt` после компиляции Kotlin должен появиться из файла `Cli.kt`, класс верхнего уровня `fun main()`).
- `src/test/kotlin/com/example/agent/` — юнит-тесты для модели (если есть логика, не только data-классы).

## Constraints / Non-Goals
- Не подключать новые внешние зависимости на этом шаге.
- CLI на этом шаге может быть минимальным (парсинг аргумента `--source <path>` и печать заглушечного сообщения) — полноценная интеграция анализа/генерации появится в задачах 02–06.
- Не удалять и не менять `build.gradle.kts`/`settings.gradle.kts` без необходимости (кроме, при необходимости, добавления `sourceSets`, если потребуется).

## Output Artifacts
- N/A (артефакты — сам код в репозитории).

## What to Do
- Удалить `src/main/kotlin/Main.kt` (или переместить его логику), создать `src/main/kotlin/com/example/agent/Cli.kt` с `fun main(args: Array<String>)`.
- Создать пакет `com.example.agent.model` со следующими data-классами (точные имена и поля обязательны — на них будут ссылаться задачи 02–07):
  ```kotlin
  package com.example.agent.model

  data class ParameterInfo(
      val name: String,
      val type: String
  )

  enum class ScenarioType { POSITIVE, NEGATIVE, BOUNDARY }

  data class BranchInfo(
      val kind: String,      // "if", "switch", "ternary" и т.п.
      val condition: String, // текстовое представление условия
      val lineNumber: Int
  )

  data class LoopInfo(
      val kind: String,      // "for", "while", "do-while"
      val condition: String,
      val lineNumber: Int
  )

  data class ExceptionInfo(
      val exceptionType: String,
      val context: String,   // например, "thrown" или "caught"
      val lineNumber: Int
  )

  data class FunctionInfo(
      val name: String,
      val className: String,
      val parameters: List<ParameterInfo>,
      val returnType: String,
      val branches: List<BranchInfo>,
      val loops: List<LoopInfo>,
      val exceptions: List<ExceptionInfo>,
      val cyclomaticComplexity: Int
  )

  data class CodeStructure(
      val sourcePath: String,
      val language: String, // "java"
      val functions: List<FunctionInfo>
  )

  data class TestCase(
      val id: String,
      val functionName: String,
      val className: String,
      val type: ScenarioType,
      val description: String,
      val inputData: Map<String, String?>, // имя параметра -> значение как строка (для сериализации/кодогенерации)
      val expectedResult: String?,
      val steps: List<String>
  )

  data class TestSuiteResult(
      val sourcePath: String,
      val testCases: List<TestCase>
  )
  ```
- Обновить `CliKt.main` так, чтобы он принимал флаг `--source <path>` и на этом этапе просто печатал `"Source: <path>"` (реальная логика анализа добавится в Task 02/03).
- Добавить как минимум один unit-тест, проверяющий, что `CodeStructure`/`TestCase` корректно создаются и их поля доступны (простая smoke-проверка компиляции модели).

## Expected Output
- Компилируемый проект с пакетом `com.example.agent` и вышеописанными data-классами.
- Отчёт исполнителя с точным списком созданных/изменённых файлов.

## Acceptance Criteria
- [ ] Пакет `com.example.agent.model` содержит все перечисленные data-классы с точными именами полей.
- [ ] `./gradlew compileKotlin compileTestKotlin` завершается успешно.
- [ ] `./gradlew test` завершается успешно (включая новый smoke-тест).
- [ ] `mainClass = "com.example.agent.CliKt"` соответствует реально существующему `fun main()` в `com.example.agent.Cli.kt`.
- [ ] Covered requirements and scenarios are satisfied (R1 база, R6).
- [ ] I've created a git commit for this task.
