# Task 02: Модуль интеграции с исходниками

**Type:** Code Modification
**Suggested agent:** Code

## Goal
Реализовать модуль загрузки Java-исходного кода: чтение одного файла, рекурсивный обход директории с фильтрацией `*.java`, и опциональная поддержка клонирования git-репозитория по URL.

## Why This Task Exists
Пояснительная записка (раздел 2.2 "Общая архитектура системы") описывает отдельный "Модуль интеграции с репозиторием", отвечающий за получение исходного кода из систем контроля версий, локальных директорий и файлов, включая предварительную обработку. Этот модуль — первый шаг конвейера перед анализом кода.

## Spec Coverage
- Requirements: R1
- Scenarios: (входной этап для S1)

## Required Inputs
- Доменная модель из Task 01: пакет `com.example.agent.model` (используется как есть, этот модуль не меняет модель).
- `build.gradle.kts` пока не содержит git-клиента (например, JGit) — если для git-интеграции нужна библиотека, добавь `org.eclipse.jgit:org.eclipse.jgit` актуальной стабильной версии (проверь совместимость с `jvmToolchain(17)`) точечно в `build.gradle.kts` и обоснуй выбор в отчёте.

## Files/Areas
- `src/main/kotlin/com/example/agent/source/` — новый пакет: `SourceLoader.kt` (или несколько файлов: `LocalFileSourceLoader.kt`, `GitSourceLoader.kt`).
- `src/test/kotlin/com/example/agent/source/` — юнит-тесты (используй `@TempDir` JUnit5 для тестовых директорий/файлов, не полагайся на реальный git clone в unit-тестах — git-функциональность тестируй с моком/интерфейсом или отдельным `@Disabled`/интеграционным тестом).

## Constraints / Non-Goals
- Git-интеграция может быть простым оборачиванием `git clone` (через JGit или через вызов системного `git` процессом) — не нужно реализовать полноценный git-клиент с ветками/тегами, достаточно клонирования HEAD во временную директорию.
- Не тестировать реальный сетевой git clone в CI/юнит-тестах — если добавляешь тест на git, изолируй его как отдельный `@Disabled`-тест или тест на локальном git-репозитории, созданном в `@TempDir` через `git init` + commit.
- Фильтрация файлов: только `*.java`, игнорировать типичные служебные директории (`.git`, `build`, `target`, `out`).

## Output Artifacts
- N/A.

## What to Do
- Реализовать интерфейс, например:
  ```kotlin
  package com.example.agent.source

  interface SourceLoader {
      fun load(location: String): List<JavaSourceFile>
  }

  data class JavaSourceFile(
      val path: String,      // абсолютный или относительный путь
      val content: String    // текст файла
  )
  ```
- Реализовать `LocalFileSourceLoader`, который:
  - если `location` — файл `*.java`, возвращает список из одного элемента;
  - если `location` — директория, рекурсивно обходит её и возвращает все `*.java`-файлы, игнорируя `.git`, `build`, `target`, `out`.
- Реализовать `GitSourceLoader` (или расширение существующего лоадера), который клонирует репозиторий по URL во временную директорию и затем переиспользует `LocalFileSourceLoader` для получения файлов.
- Добавить unit-тесты на `LocalFileSourceLoader`: один файл, директория с вложенными поддиректориями, директория со смешанными расширениями (проверка фильтрации).
- Обновить `Cli.kt` из Task 01: флаг `--source <path>` теперь должен реально вызывать `SourceLoader` и печатать количество найденных `.java`-файлов.

## Expected Output
- Компилируемый и протестированный модуль `com.example.agent.source`.
- Обновлённый `Cli.kt`, реально использующий `SourceLoader`.
- Отчёт с точным списком файлов и (если добавлена) обоснованием добавленной зависимости для git.

## Acceptance Criteria
- [ ] `LocalFileSourceLoader` корректно обрабатывает единичный файл и директорию с вложенностью, отфильтровывая не-`.java` файлы и служебные директории.
- [ ] Есть unit-тесты, покрывающие оба сценария (файл / директория), использующие `@TempDir`.
- [ ] `./gradlew build` проходит успешно.
- [ ] `Cli.kt` реально вызывает `SourceLoader` и выводит осмысленный результат.
- [ ] Covered requirements and scenarios are satisfied (R1).
- [ ] I've created a git commit for this task.
