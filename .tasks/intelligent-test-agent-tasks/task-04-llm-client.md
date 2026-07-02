# Task 04: LLM-клиент (OkHttp)

**Type:** Code Modification
**Suggested agent:** Code

## Goal
Реализовать конфигурируемый HTTP-клиент для внешнего LLM API (OpenAI-совместимый Chat Completions REST-протокол по умолчанию), использующий `com.squareup.okhttp3:okhttp:4.12.0`, с обязательным безопасным fallback-поведением, если ключ API/сеть недоступны.

## Why This Task Exists
Пояснительная записка описывает семантическую интерпретацию кода и генерацию тестов с помощью больших языковых моделей (разделы 1.4, 2.4, 2.5) как важное дополнение к формальным (эвристическим) методам. Этот модуль — единая точка вызова LLM, которую будет использовать модуль генерации сценариев (Task 05).

## Spec Coverage
- Requirements: R2
- Scenarios: (входной компонент для R2/S2, напрямую не проверяется отдельным acceptance-сценарием)

## Required Inputs
- Библиотека `com.squareup.okhttp3:okhttp:4.12.0` уже объявлена в `build.gradle.kts`.
- Jackson (`com.fasterxml.jackson.module:jackson-module-kotlin:2.15.3`) уже объявлен — использовать для сериализации/десериализации тела запроса/ответа.
- Конфигурация клиента (base URL, API key, имя модели) должна читаться из переменных окружения, например: `LLM_API_BASE_URL` (по умолчанию `https://api.openai.com/v1`), `LLM_API_KEY` (без значения по умолчанию — при отсутствии клиент должен явно переходить в fallback-режим), `LLM_MODEL` (по умолчанию, например, `gpt-4o-mini`).

## Files/Areas
- `src/main/kotlin/com/example/agent/llm/` — новый пакет: `LlmClient.kt`, `LlmConfig.kt`.
- `src/test/kotlin/com/example/agent/llm/` — unit-тесты (с использованием `okhttp3.mockwebserver` — если библиотека не подключена, добавь `implementation("com.squareup.okhttp3:mockwebserver:4.12.0")` в `testImplementation` в `build.gradle.kts`, версия должна совпадать с основной OkHttp).

## Constraints / Non-Goals
- Не хардкодить API-ключ в код — только через переменную окружения/конфиг-объект, передаваемый явно.
- Не делать реальных сетевых вызовов к настоящему OpenAI API в unit-тестах — использовать `MockWebServer` из `okhttp3.mockwebserver` для проверки формирования запроса и разбора ответа.
- Клиент должен экспонировать чистый доменный интерфейс (не завязанный на конкретный JSON-формат конкретного провайдера) — например, `fun complete(prompt: String): LlmResult`, где `LlmResult` — sealed-класс с вариантами `Success(text: String)` и `Failure(reason: String)`.
- Таймауты подключения/чтения должны быть разумными и настраиваемыми (например, 30 секунд по умолчанию), чтобы не блокировать сборку/тесты навсегда при недоступности сети.

## Output Artifacts
- N/A.

## What to Do
- Реализовать:
  ```kotlin
  package com.example.agent.llm

  data class LlmConfig(
      val baseUrl: String,
      val apiKey: String?,
      val model: String
  ) {
      companion object {
          fun fromEnv(): LlmConfig
      }
  }

  sealed class LlmResult {
      data class Success(val text: String) : LlmResult()
      data class Failure(val reason: String) : LlmResult()
  }

  interface LlmClient {
      fun complete(prompt: String): LlmResult
  }

  class OpenAiCompatibleLlmClient(private val config: LlmConfig) : LlmClient {
      override fun complete(prompt: String): LlmResult
  }
  ```
- `LlmConfig.fromEnv()` читает `LLM_API_BASE_URL`, `LLM_API_KEY`, `LLM_MODEL` из `System.getenv()`, подставляя дефолты, где применимо.
- `OpenAiCompatibleLlmClient.complete`:
  - если `config.apiKey.isNullOrBlank()` — сразу вернуть `LlmResult.Failure("No API key configured")` без сетевого вызова;
  - иначе сформировать HTTP POST на `${baseUrl}/chat/completions` с телом в формате OpenAI Chat Completions API (`model`, `messages: [{role: "user", content: prompt}]`), заголовком `Authorization: Bearer <apiKey>`;
  - при успешном ответе (200) распарсить текст из `choices[0].message.content` и вернуть `Success`;
  - при сетевой ошибке, таймауте или ошибке HTTP-статуса — вернуть `Failure` с описанием причины, не бросая исключение наружу.
- Написать unit-тесты с `MockWebServer`:
  1. Успешный ответ 200 с валидным JSON → `LlmResult.Success` с ожидаемым текстом.
  2. Ответ с HTTP 500 → `LlmResult.Failure`.
  3. Пустой/отсутствующий `apiKey` в конфиге → `LlmResult.Failure` без обращения к серверу (проверить, что `MockWebServer` не получил запрос, через `server.requestCount == 0`).
- Добавить простую заготовку шаблонов промптов (например, `PromptTemplates.kt`) с функцией вида `fun functionAnalysisPrompt(functionInfo: FunctionInfo): String`, формирующей текстовый промпт на основе `com.example.agent.model.FunctionInfo` (из Task 01) для последующего использования в Task 05.

## Expected Output
- Модуль `com.example.agent.llm` с описанным API.
- Unit-тесты на `MockWebServer`, покрывающие успех/ошибку/fallback без ключа.
- Обновлённый `build.gradle.kts`, если добавлена зависимость `mockwebserver` в `testImplementation`.

## Acceptance Criteria
- [ ] `LlmClient`/`OpenAiCompatibleLlmClient` реализованы согласно описанному API и не бросают необработанные исключения при сетевых ошибках.
- [ ] При отсутствии `LLM_API_KEY` клиент детерминированно возвращает `Failure` без сетевого вызова (проверено тестом).
- [ ] Unit-тесты на `MockWebServer` покрывают успех и ошибку HTTP.
- [ ] `./gradlew build` и `./gradlew test` проходят успешно.
- [ ] Covered requirements and scenarios are satisfied (R2).
- [ ] I've created a git commit for this task.
