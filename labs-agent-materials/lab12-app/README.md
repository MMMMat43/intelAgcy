# ЛР 12. Приложение «Журнал запусков генерации тестов»

Настольное приложение на Kotlin (Swing) для просмотра и анализа истории запусков генерации тестов. Данные берутся из базы `agent_history.db`, спроектированной в ЛР 11. Приложение входит в состав агента и использует его слой доступа к данным (`RunRepository`, `SqliteRunRepository`).

## Возможности

- Таблица запусков: проект, время начала, статус, длительность, число функций и тест-кейсов, покрытые ветви, процент покрытия.
- Фильтр: проект, диапазон покрытия в процентах, только запуски с непокрытыми ветвями.
- Детали выбранного запуска на вкладках:
  - «Функции»: класс, функция, параметры, цикломатическая сложность, ветви, покрытие, число тест-кейсов;
  - «Ветви»: функция, идентификатор ветви, условие, состояние (покрыта, не найдено значение, не инструментируется), с подсветкой;
  - «Тест-кейсы»: функция, вид сценария, источник значений, входные данные, ожидаемый результат;
  - «Заметки»: заметки к запуску.
- Сводка по выборке: число запусков и проектов, среднее покрытие, число тест-кейсов и непокрытых ветвей, самые сложные функции.
- Добавление заметки к запуску (таблица `note`).
- Удаление запуска с подтверждением (каскадное удаление функций, ветвей, тест-кейсов, артефактов и заметок).

## Запуск

```powershell
# окно приложения; эталонная база открывается копией во временном файле
.\run.ps1 history-ui

# своя база (например, записанная агентом через --history)
.\run.ps1 history-ui -Database путь\к\agent_history.db

# экранные формы в PNG без открытия окна (headless)
.\run.ps1 history-ui -Screenshot labs-agent-materials\lab12-app\screenshots
```

Без параметра `-Database` приложение копирует `labs-agent-materials/lab11-database/agent_history.db` во временный файл и работает с копией. Так добавление заметок и удаление запусков при демонстрации не портят эталонную базу ЛР 11.

Получить свою историю запусков можно через агента:

```powershell
.\run.ps1 generate -Source src\test\resources\OrderProcessor.kt
# или напрямую: CLI с флагом --history my.db (запись истории включается только по флагу)
```

## Структура

| Пакет / файл | Назначение |
|---|---|
| `src/main/kotlin/com/example/agent/history/HistoryService.kt` | Логика без Swing: список запусков с фильтрами, детали, сводка, заметки, удаление |
| `src/main/kotlin/com/example/agent/history/HistoryModels.kt` | Модели данных: `RunFilter`, `RunSummaryRow`, `RunDetails`, `FunctionRow`, `BranchRow`, `TestCaseRow`, `HistoryStatistics` |
| `src/main/kotlin/com/example/agent/history/ui/HistoryView.kt` | Главное окно: фильтр, таблица запусков, вкладки, сводка |
| `src/main/kotlin/com/example/agent/history/ui/HistoryTableModels.kt` | Модели таблиц Swing (`AbstractTableModel`) |
| `src/main/kotlin/com/example/agent/history/ui/HistoryDialogs.kt` | Диалоги заметки и подтверждения удаления |
| `src/main/kotlin/com/example/agent/history/ui/HistoryFormat.kt` | Форматирование дат, процентов, состояний |
| `src/main/kotlin/com/example/agent/history/ui/HistoryTheme.kt` | Шрифт с кириллицей, цвета, оформление таблиц |
| `src/main/kotlin/com/example/agent/history/ui/HistoryScreenshots.kt` | Отрисовка экранных форм в PNG |
| `src/main/kotlin/com/example/agent/history/ui/HistoryApp.kt` | Точка входа (`HistoryAppKt`), разбор аргументов, копия базы |
| `src/main/kotlin/com/example/agent/storage/RunRepository.kt`, `SqliteRunRepository.kt` | Доступ к данным: чтение, удаление, заметки |
| `src/main/resources/db/schema.sql` | Схема базы (таблица `note` добавлена в этой работе) |

Swing загружается только через отдельную точку входа `com.example.agent.history.ui.HistoryAppKt` (Gradle-задача `historyUi`). REST-сервер и CLI агента Swing не используют, Docker-образ и тесты работают без дисплея.

## Экранные формы

Каталог `screenshots/`. Изображения отрисованы кодом самого приложения (`printAll` в `BufferedImage`) на реальных данных базы ЛР 11, а не нарисованы отдельно.

| Файл | Что показано |
|---|---|
| `01-main-runs-functions.png` | Главное окно: фильтр, запуски, сводка, вкладка «Функции» |
| `02-tab-branches.png` | Вкладка «Ветви» с подсветкой покрытых ветвей |
| `03-tab-test-cases.png` | Вкладка «Тест-кейсы» с входными данными и ожидаемым результатом |
| `04-filter-and-summary.png` | Применённый фильтр (проект OrderProcessor, покрытие от 90 %) и сводка по выборке |
| `05-dialog-note.png` | Диалог добавления заметки |
| `06-dialog-delete.png` | Подтверждение удаления запуска |
| `07-tab-notes.png` | Вкладка «Заметки» |

## Проектирование (CASE)

Каталог `design/` (PlantUML, исходники `.puml`, изображения `.png` и `.svg`):

| Файл | Диаграмма |
|---|---|
| `01-use-cases.puml` | Варианты использования |
| `02-sequence-branches.puml` | Последовательность: выбор запуска и вкладка «Ветви» |
| `03-layers.puml` | Компоненты и слои: UI → HistoryService → RunRepository → SQLite |
| `04-window-mockup.puml` | Макет главного окна (PlantUML Salt) |

Диаграмма классов приложения построена автоматически по коду генератором из ЛР 10: `labs-agent-materials/lab10-uml/out/07-history-app.png` (команда `.\run.ps1 uml`).

Пересобрать проектные диаграммы:

```powershell
java -jar labs-agent-materials\lab10-uml\tools\bin\plantuml.jar -tpng -charset UTF-8 labs-agent-materials\lab12-app\design\*.puml
```

Сравнение CASE-средств: `comparison.md`.

## Тесты

- `src/test/kotlin/com/example/agent/history/HistoryServiceTest.kt`: фильтры, сводка, детали, заметки, каскадное удаление на временной базе SQLite.
- `src/test/kotlin/com/example/agent/history/ui/HistoryUiTest.kt`: модели таблиц, поведение окна, отрисовка в headless-режиме, запуск с копией базы.
