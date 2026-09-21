# Проверка лабораторных работ №9–12

Дата проверки: 2026 г. Среда: Windows 11, Java/JBR 21 с компиляцией `--release 17`, Gradle 9.6.0, Python 3.13, SQLite 3 (модуль Python), PlantUML 1.2025.2.

## Сборка и unit-тесты

```powershell
$env:JAVA_HOME = "C:/Program Files/JetBrains/PyCharm 2025.2.3/jbr"
.\gradlew.bat clean test --no-daemon
```

Фактический результат: `BUILD SUCCESSFUL`; 4 теста, 0 failures, 0 errors.

- `lab09-patterns`: 2 теста — совместная работа Strategy/Factory/Observer/Repository и смена стратегии.
- `lab12-case-app`: 2 теста — создание/чтение проекта в реальной временной SQLite БД и сервисная валидация.

## Запуск ЛР9

```powershell
.\gradlew.bat :lab09-patterns:run --no-daemon
```

Фактический результат: анализ одного метода; observer вывел статистику; созданы граничный и негативный сценарии; `BUILD SUCCESSFUL`.

## PlantUML

```powershell
java -jar tools\plantuml.jar -tpng lab10-uml\class-diagram.puml
java -jar tools\plantuml.jar -tsvg lab10-uml\class-diagram.puml
java -jar tools\plantuml.jar -tpng lab11-database\er-diagram.puml
java -jar tools\plantuml.jar -tsvg lab11-database\er-diagram.puml
java -jar tools\plantuml.jar -tpng lab12-case-app\architecture.puml
```

Фактический результат: сформированы PNG и SVG без ошибок синтаксиса.

## SQLite

```powershell
py scripts\create_database.py
py scripts\verify_database.py
```

Фактический результат:

- `PRAGMA integrity_check`: `ok`;
- нарушений внешних ключей: 0;
- запрос 1: 2 строки (проекты и число тестов);
- запрос 2: 3 строки (методы сложности >= 4);
- запрос 3: 3 строки (артефакты завершённых запусков).

## ЛР12

```powershell
.\gradlew.bat :lab12-case-app:run --no-daemon
```

Swing-приложение реально запущено с файлом `lab11-database/intelligent_test_agent.db`; процесс стартовал и прочитал данные без исключений. После проверки процесс остановлен. Из-за ограничений фоновой оконной сессии в отчёт включено честно обозначенное программно сформированное представление реальной формы `screenshots/main-form.png`.

## DOCX

Все четыре отчёта открыты Microsoft Word через COM, выполнена репагинация; диаграммы встроены.

| Отчёт | Страниц |
|---|---:|
| ЛР09_Шаблоны_проектирования.docx | 13 |
| ЛР10_UML_диаграммы.docx | 12 |
| ЛР11_CASE_база_данных.docx | 12 |
| ЛР12_CASE_приложение.docx | 13 |

Формат: Times New Roman 14, межстрочный интервал 1,5; код — Consolas 9,5; учебные поля страницы; рисунки и реальные листинги без заглушек.
