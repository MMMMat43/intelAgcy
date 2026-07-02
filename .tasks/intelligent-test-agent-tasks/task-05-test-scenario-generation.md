# Task 05: Модуль генерации тестовых сценариев

**Type:** Code Modification
**Suggested agent:** Code

## Goal
Реализовать генерацию тестовых сценариев (позитивных, негативных, граничных) на основе `CodeStructure`/`FunctionInfo` (Task 03), комбинируя формальные эвристики (граничный анализ по типам параметров) с семантическими подсказками от LLM-клиента (Task 04). Результат — список `TestCase` (модель из Task 01), пригодный для сериализации в JSON.

## Why This Task Exists
Это ядро раздела 2.5 ПЗ ("Алгоритмы генерации тестовых сценариев и тест-кейсов") — центральная функция агента, преобразующая структурную информацию о коде в конкретные проверяемые тест-кейсы трёх типов, с последующей постобработкой (устранение дублей, унификация формулировок).

## Spec Coverage
- Requirements: R2
- Scenarios: (входной этап для S2)

## Required Inputs
- `com.example.agent.model.{FunctionInfo, TestCase, ScenarioType, TestSuiteResult}` из Task 01 — использовать как есть.
- `com.example.agent.llm.{LlmClient, LlmResult}` и `PromptTemplates.functionAnalysisPrompt` из Task 04.
- `com.fasterxml.jackson.module:jackson-module-kotlin:2.15.3` — уже объявлен, использовать для сериализации `TestSuiteResult` в JSON.

## Files/Areas
- `src/main/kotlin/com/example/agent/generation/` — новый пакет: `HeuristicScenarioGenerator.kt`, `LlmScenarioEnricher.kt`, `TestScenarioGenerator.kt` (фасад, объединяющий эвристики и LLM), `TestCasePostProcessor.kt` (дедупликация/нормализация).
- `src/test/kotlin/com/example/agent/generation/` — unit-тесты.

## Constraints / Non-Goals
- Эвристическая часть должна работать полностью автономно (без LLM) и покрывать минимум: граничные значения для числовых типов параметров (`int`, `long`, `double` и их boxed-аналогов: min/max/0/отрицательное), пустые/null/очень длинные строки для `String`, null-проверки для ссылочных типов, минимум один позитивный сценарий "типичные валидные значения".
- LLM используется только как *обогащение* (дополнительные сценарии/описания), а не единственный источник — если `LlmClient.complete(...)` возвращает `Failure`, генератор обязан вернуть валидный непустой результат только на основе эвристик (не бросать исключение, не возвращать пустой список).
- Постобработка обязана убирать дублирующиеся сценарии (по совпадению `functionName + type + inputData`).
- Не пытаться реализовать полноценный branch/condition coverage solver — достаточно эвристик, описанных выше, плюс использование данных о `branches`/`exceptions` из `FunctionInfo` для формирования релевантных описаний/сценариев (например, отдельный негативный сценарий на каждый `ExceptionInfo` с context="thrown").

## Output Artifacts
- N/A.

## What to Do
- Реализовать `HeuristicScenarioGenerator`:
  ```kotlin
  package com.example.agent.generation

  import com.example.agent.model.FunctionInfo
  import com.example.agent.model.TestCase

  class HeuristicScenarioGenerator {
      fun generate(function: FunctionInfo): List<TestCase>
  }
  ```
  — реализовать правила из секции Constraints выше, присваивая каждому `TestCase` уникальный `id` (например, `"${function.name}-${counter}"`).
- Реализовать `LlmScenarioEnricher`, который принимает `FunctionInfo`, вызывает `LlmClient.complete(PromptTemplates.functionAnalysisPrompt(function))`, и при `Success` — парсит текст ответа в дополнительные `TestCase` (если модель LLM возвращает не полностью структурированный текст, допускается простой построчный/эвристический парсинг ответа в описания сценариев — не требуется strict JSON-schema валидация на этом этапе), при `Failure` — возвращает пустой список.
- Реализовать `TestCasePostProcessor.deduplicateAndNormalize(cases: List<TestCase>): List<TestCase>` — убирает дубли по `(functionName, type, inputData)`, нормализует пробелы в `description`.
- Реализовать фасад:
  ```kotlin
  class TestScenarioGenerator(
      private val heuristicGenerator: HeuristicScenarioGenerator,
      private val llmEnricher: LlmScenarioEnricher,
      private val postProcessor: TestCasePostProcessor
  ) {
      fun generateForStructure(structure: com.example.agent.model.CodeStructure): com.example.agent.model.TestSuiteResult
  }
  ```
- Написать unit-тесты:
  1. `HeuristicScenarioGenerator` на функции с одним `int`-параметром — проверить, что среди сгенерированных `TestCase` есть минимум min/max/0/отрицательное значение и один позитивный сценарий.
  2. `HeuristicScenarioGenerator` на функции с `String`-параметром — проверить наличие сценариев с пустой строкой и null.
  3. `TestScenarioGenerator` с моком `LlmClient`, возвращающим `Failure` — убедиться, что результат непустой и содержит только эвристические сценарии.
  4. `TestCasePostProcessor` — проверка удаления явных дублей.
- Обновить `Cli.kt`: добавить флаг (например, `--generate-tests`), который после анализа (Task 03) вызывает `TestScenarioGenerator` и печатает JSON (Jackson) с результатом `TestSuiteResult`.

## Expected Output
- Модуль `com.example.agent.generation` с описанным API, работающий полностью автономно без LLM и опционально обогащаемый LLM.
- Unit-тесты, покрывающие 4 описанных сценария.
- Обновлённый `Cli.kt`.

## Acceptance Criteria
- [ ] `HeuristicScenarioGenerator` генерирует минимум позитивный + граничные + null/empty сценарии согласно правилам для числовых и строковых типов.
- [ ] `TestScenarioGenerator` возвращает непустой валидный результат даже при `LlmClient` в состоянии `Failure` (проверено тестом с моком).
- [ ] `TestCasePostProcessor` убирает дубликаты (проверено тестом).
- [ ] `./gradlew build` и `./gradlew test` проходят успешно.
- [ ] Covered requirements and scenarios are satisfied (R2).
- [ ] I've created a git commit for this task.
