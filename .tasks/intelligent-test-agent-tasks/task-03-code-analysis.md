# Task 03: Модуль анализа Java-кода (JavaParser)

**Type:** Code Modification
**Suggested agent:** Code

## Goal
Реализовать статический анализ Java-исходников с использованием `com.github.javaparser:javaparser-core:3.25.10`: построение AST, извлечение функций/методов, параметров, ветвлений, циклов, обработчиков исключений, и вычисление цикломатической сложности каждой функции. Результат — заполнение доменной модели `CodeStructure`/`FunctionInfo` из Task 01.

## Why This Task Exists
Это ядро пояснительной записки (раздел 2.4 "Алгоритм анализа исходного кода"): статический анализ на основе AST, выявление логических конструкций (условия, циклы, исключения), формирование метрик сложности (цикломатическая сложность) для последующей генерации тестов. Без структурированного результата этого модуля генерация сценариев (Task 05) не имеет входных данных.

## Spec Coverage
- Requirements: R1
- Scenarios: S1

## Required Inputs
- Доменная модель из Task 01: `com.example.agent.model.{CodeStructure, FunctionInfo, ParameterInfo, BranchInfo, LoopInfo, ExceptionInfo}` — использовать эти точные классы и поля, не создавать параллельную модель.
- Модуль `com.example.agent.source.JavaSourceFile` из Task 02 — вход для анализатора (список `JavaSourceFile` → `CodeStructure`).
- Библиотека: `com.github.javaparser:javaparser-core:3.25.10` (уже объявлена в `build.gradle.kts`, дополнительно подключать не нужно).

## Files/Areas
- `src/main/kotlin/com/example/agent/analysis/` — новый пакет: `JavaCodeAnalyzer.kt` (или разбить на `AstVisitor.kt`, `ComplexityCalculator.kt`).
- `src/test/kotlin/com/example/agent/analysis/` — unit-тесты на реальных небольших Java-сниппетах (передаваемых как строки, не требующих файлов на диске).

## Constraints / Non-Goals
- Не реализовывать полноценный межпроцедурный анализ потоков данных — достаточно внутрипроцедурного (per-method) анализа согласно ПЗ.
- Цикломатическая сложность считается по стандартной формуле: `M = E - N + 2P` либо эквивалентно `1 + количество точек принятия решений` (if, else if, case, for, while, do-while, catch, `&&`/`||` в условиях, тернарный оператор) — выбери один общепринятый вариант подсчёта и явно задокументируй его в коде (KDoc).
- Не нужно поддерживать все экзотические конструкции Java (лямбды со сложной логикой, вложенные анонимные классы) в первой итерации — достаточно распространённых конструкций методов/классов верхнего уровня и вложенных if/for/while/try-catch.

## Output Artifacts
- N/A.

## What to Do
- Реализовать класс/функцию, например:
  ```kotlin
  package com.example.agent.analysis

  import com.example.agent.model.CodeStructure
  import com.example.agent.source.JavaSourceFile

  class JavaCodeAnalyzer {
      fun analyze(sourcePath: String, files: List<JavaSourceFile>): CodeStructure
  }
  ```
- Внутри использовать `com.github.javaparser.StaticJavaParser` (или `JavaParser`) для парсинга каждого файла в `CompilationUnit`, обойти классы/интерфейсы и их методы (`MethodDeclaration`), для каждого метода:
  - извлечь имя, имя содержащего класса, параметры (имя+тип), тип возврата;
  - обойти тело метода и собрать `BranchInfo` (if/else if/switch/ternary) с номером строки и текстом условия;
  - собрать `LoopInfo` (for/while/do-while) с номером строки и условием;
  - собрать `ExceptionInfo` (throw-выражения и catch-блоки) с типом исключения и контекстом ("thrown"/"caught");
  - вычислить `cyclomaticComplexity` по задокументированной формуле.
- Написать unit-тесты как минимум на 3 сценария:
  1. Метод без ветвлений — цикломатическая сложность = 1, пустые списки branches/loops/exceptions.
  2. Метод с if/else, for-циклом и одним throw — проверка, что все элементы корректно извлечены и сложность посчитана верно вручную.
  3. Метод с try/catch на несколько типов исключений — проверка `ExceptionInfo` со значением context = "caught" для каждого catch-блока.
- Обновить `Cli.kt`: после загрузки исходников (Task 02) вызвать анализатор и вывести JSON-представление `CodeStructure` (можно временно через `toString()` или простую сериализацию — полноценная Jackson-сериализация появится в Task 06/07, но допустимо использовать Jackson уже здесь, если это упрощает вывод).

## Expected Output
- Модуль `com.example.agent.analysis.JavaCodeAnalyzer`, реализующий описанный API.
- Unit-тесты, покрывающие минимум 3 описанных сценария, с ручной проверкой ожидаемой цикломатической сложности.
- Обновлённый `Cli.kt`, реально печатающий результат анализа.

## Acceptance Criteria
- [ ] `JavaCodeAnalyzer.analyze(...)` возвращает `CodeStructure` с корректно заполненными `functions`, включая `branches`, `loops`, `exceptions`, `cyclomaticComplexity`.
- [ ] Формула подсчёта цикломатической сложности задокументирована в KDoc и покрыта тестом с известным ожидаемым значением.
- [ ] Есть минимум 3 unit-теста согласно описанным сценариям, все проходят.
- [ ] `./gradlew build` проходит успешно.
- [ ] Covered requirements and scenarios are satisfied (R1, S1).
- [ ] I've created a git commit for this task.
