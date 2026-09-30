# ЛР 11. База данных истории запусков агента

Предметная область: хранение и анализ результатов работы агента генерации тестов (проекты, запуски, функции, ветви, тест-кейсы, артефакты).

## Состав

| Файл | Назначение |
|---|---|
| `../../src/main/resources/db/schema.sql` | единственный источник схемы (DDL для SQLite), по ней работает и агент |
| `seed.sql` | демонстрационные данные из реальных запусков агента |
| `queries.sql` | 12 содержательных запросов |
| `agent_history.db` | готовая база |
| `create_database.py` | пересоздаёт базу из схемы и из результатов запусков агента |
| `verify_database.py` | проверка: целостность, внешние ключи, число строк, выполнение запросов |
| `make_er.py` | строит ER-диаграмму по фактической структуре файла базы |
| `er-diagram.puml`, `.png`, `.svg` | ER-диаграмма |
| `design.md` | описание проектирования: концептуальная, логическая, физическая модели |
| `comparison.md` | сравнение CASE-средств для проектирования баз данных |

## Команды

Пересоздание базы из реальных результатов агента:

```powershell
.\run.ps1 generate -Source src\test\resources\OrderProcessor.kt -Output <каталог>\OrderProcessor
py labs-agent-materials\lab11-database\create_database.py --runs <каталог>
py labs-agent-materials\lab11-database\verify_database.py
py labs-agent-materials\lab11-database\make_er.py
```

Запись истории самим агентом (по умолчанию выключена):

```powershell
.\run.ps1 generate -Source src\test\resources\OrderProcessor.kt
# или напрямую: --history history.db либо переменная окружения AGENT_HISTORY_DB
```

## Результат проверки

`PRAGMA integrity_check`: `ok`. `PRAGMA foreign_key_check`: 0 нарушений. Строки в таблицах: `project` 3, `source_file` 3, `run` 3, `function_info` 9, `branch` 55, `test_case` 92, `test_input` 199, `artifact` 12, справочники по 3.
