# Лабораторные работы №9–12

Связный учебный мини-проект по теме магистерской работы: интеллектуальный агент анализа Java-кода и генерации тестов.

## Структура
- `lab09-patterns` — Java 17-приложение с Strategy, Abstract Factory, Observer, Repository.
- `lab10-uml` — PlantUML-исходник и изображения диаграммы классов.
- `lab11-database` — SQLite-схема, данные, запросы, DBML/ER-диаграмма и реальная БД.
- `lab12-case-app` — Swing-приложение DAO/Repository + Service + UI.
- `reports` — четыре отчёта DOCX.

## Команды
```powershell
# Сборка и все unit-тесты
.\gradlew.bat clean test
# ЛР9
.\gradlew.bat :lab09-patterns:run
# Пересоздание БД (Python входит в стандартную поставку)
py scripts/create_database.py
# Проверка БД
py scripts/verify_database.py
# ЛР12
.\gradlew.bat :lab12-case-app:run --args="lab11-database/intelligent_test_agent.db"
```
