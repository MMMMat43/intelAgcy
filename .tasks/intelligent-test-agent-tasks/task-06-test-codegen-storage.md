# Task 06: Генерация JUnit5-кода и хранение артефактов

**Type:** Code Modification
**Suggested agent:** Code

## Goal
Реализовать генерацию компилируемого исходного кода JUnit5-тестов на Kotlin из `TestSuiteResult` (Task 05), используя `com.squareup:kotlinpoet:1.16.0`, и модуль сохранения артефактов (анализ, JSON тест-кейсов, сгенерированный код) на диск в структурированном виде.

## Why This Task Exists
Раздел 2.5/"Выбор формата представления тестов" ПЗ описывает необходимость трансформации структурированных тест-кейсов в исполняемый программный код для интеграции в CI/CD, а также хранения текстового/JSON-представления для документирования. Это последний шаг конвейера перед выставлением REST API (Task 07).

## Spec Coverage
- Requirements: R3, R5
- Scenarios: (входной этап для S2)

## Required Inputs
- `com.example.agent.model.{TestCase, TestSuiteResult, ScenarioType}` из Task 01.
- `com.squareup:kotlinpoet:1.16.0` — уже объявлен в `build.gradle.kts`.
- `com.fasterxml.jackson.module:jackson-module-kotlin:2.15.3` — уже объявлен, для сериализации JSON.

## Files/Areas
- `src/main/kotlin/com/example/agent/codegen/` — новый пакет: `JUnit5TestCodeGenerator.kt`.
- `src/main/kotlin/com/example/agent/storage/` — новый пакет: `ArtifactStorage.kt`.
- `src/test/kotlin/com/example/agent/codegen/` и `src/test/kotlin/com/example/agent/storage/` — unit-тесты.

## Constraints / Non-Goals
- Сгенерированный Kotlin-код тестов должен быть *синтаксически корректным и компилируемым* — для верификации в unit-тесте достаточно проверить, что `KotlinPoet` успешно строит `FileSpec` и его `toString()` не пуст и содержит ожидаемые аннотации (`@Test`, импорт `org.junit.jupiter.api.Test`, `org.junit.jupiter.api.Assertions.*` или `kotlin.test.*`). Полная компиляция сгенерированного файла отдельным Gradle-таском не требуется в рамках этой задачи (проверяется в Task 08 на реальном сквозном примере).
- Каждый метод теста в сгенерированном классе должен иметь осмысленное имя (например, `test_<functionName>_<scenarioType>_<index>`), аннотацию `@Test`, и тело с TODO/заглушкой вызова тестируемого метода с параметрами из `inputData` (полный вызов реального метода не обязателен, если сигнатура вызываемого класса не создаётся автоматически — но структура теста, включая assert-заглушку, должна присутствовать).
- `ArtifactStorage` должен создавать предсказуемую структуру директорий, например: `<outputDir>/analysis.json`, `<outputDir>/test-cases.json`, `<outputDir>/generated-tests/<ClassName>Test.kt`.

## Output Artifacts
- N/A.

## What to Do
- Реализовать:
  ```kotlin
  package com.example.agent.codegen

  import com.example.agent.model.TestSuiteResult
  import com.squareup.kotlinpoet.FileSpec

  class JUnit5TestCodeGenerator {
      fun generate(testSuite: TestSuiteResult, packageName: String): FileSpec
  }
  ```
  — группировать `TestCase` по `className`, генерировать один Kotlin-файл на класс (или один файл с несколькими тестовыми классами — на усмотрение реализации, но задокументировать выбор), для каждого `TestCase` генерировать метод с `@Test`-аннотацией, javadoc/kdoc с `description`, телом, включающим комментарии со `steps` и заглушку assert (например, `// TODO: assert expected = ${testCase.expectedResult}`).
- Реализовать:
  ```kotlin
  package com.example.agent.storage

  import com.example.agent.model.CodeStructure
  import com.example.agent.model.TestSuiteResult
  import com.squareup.kotlinpoet.FileSpec
  import java.nio.file.Path

  class ArtifactStorage(private val outputDir: Path) {
      fun saveAnalysis(structure: CodeStructure)
      fun saveTestCases(testSuite: TestSuiteResult)
      fun saveGeneratedTestCode(fileSpec: FileSpec)
  }
  ```
  — методы создают недостающие директории, сериализуют через Jackson (`analysis.json`, `test-cases.json`) и записывают Kotlin-файл через `FileSpec.writeTo(outputDir.resolve("generated-tests"))` (стандартный API KotlinPoet для записи).
- Написать unit-тесты:
  1. `JUnit5TestCodeGenerator` на `TestSuiteResult` с 2-3 `TestCase` разных `ScenarioType` для одной функции — проверить, что сгенерированный `FileSpec.toString()` содержит нужное количество `@Test`-методов и импорт JUnit5.
  2. `ArtifactStorage` с `@TempDir` — проверить, что после вызова всех трёх методов на диске появляются `analysis.json`, `test-cases.json` и `.kt`-файл в `generated-tests/`, и что JSON корректно десериализуется обратно в исходные модели (round-trip тест).
- Обновить `Cli.kt`: добавить флаг `--output <dir>`, при указании которого после анализа и генерации сценариев (Task 03, Task 05) вызываются методы `ArtifactStorage` для сохранения всех трёх артефактов.

## Expected Output
- Модули `com.example.agent.codegen` и `com.example.agent.storage` с описанным API.
- Unit-тесты, включая round-trip проверку JSON.
- Обновлённый `Cli.kt` с флагом `--output`.

## Acceptance Criteria
- [ ] `JUnit5TestCodeGenerator.generate(...)` возвращает `FileSpec`, `toString()` которого содержит корректные `@Test`-методы и необходимые импорты JUnit5.
- [ ] `ArtifactStorage` сохраняет три вида артефактов в предсказуемой структуре директорий (проверено тестом с `@TempDir`).
- [ ] JSON-артефакты проходят round-trip десериализацию без потери данных (проверено тестом).
- [ ] `./gradlew build` и `./gradlew test` проходят успешно.
- [ ] Covered requirements and scenarios are satisfied (R3, R5).
- [ ] I've created a git commit for this task.
