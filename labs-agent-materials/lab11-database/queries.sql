-- Запросы к базе истории запусков агента (SQLite).

-- Q1. Сводка запусков: проект, время, число функций и покрытие ветвей, %
SELECT p.name AS project, r.started_at, r.status,
       COUNT(f.id) AS functions,
       SUM(f.covered_branches) || '/' || SUM(f.total_branches) AS branches,
       ROUND(100.0 * SUM(f.covered_branches) / NULLIF(SUM(f.total_branches), 0), 1) AS coverage_pct
FROM run r
JOIN project p ON p.id = r.project_id
LEFT JOIN function_info f ON f.run_id = r.id
GROUP BY r.id
ORDER BY r.started_at;

-- Q2. Пять самых сложных функций по цикломатической сложности
SELECT f.class_name, f.function_name, f.complexity, f.total_branches
FROM function_info f
WHERE f.complexity IS NOT NULL
ORDER BY f.complexity DESC, f.function_name
LIMIT 5;

-- Q3. Функции, у которых не все ветви покрыты
SELECT f.class_name, f.function_name, f.covered_branches, f.total_branches
FROM function_info f
WHERE f.covered_branches < f.total_branches
ORDER BY f.total_branches - f.covered_branches DESC;

-- Q4. Непокрытые ветви с текстом условия
SELECT f.class_name || '.' || f.function_name AS function_name, b.code, b.label, b.state
FROM branch b
JOIN function_info f ON f.id = b.function_id
WHERE b.state <> 'COVERED'
ORDER BY function_name, b.code;

-- Q5. Распределение тест-кейсов по видам сценария
SELECT t.name AS scenario_type, COUNT(*) AS cases
FROM test_case c
JOIN scenario_type t ON t.id = c.scenario_type_id
GROUP BY t.name
ORDER BY cases DESC;

-- Q6. Распределение тест-кейсов по источнику значений
SELECT o.name AS origin, COUNT(*) AS cases
FROM test_case c
JOIN case_origin o ON o.id = c.origin_id
GROUP BY o.name
ORDER BY cases DESC;

-- Q7. Число тест-кейсов на функцию и плотность на одну ветвь
SELECT f.class_name || '.' || f.function_name AS function_name,
       COUNT(c.id) AS cases,
       f.total_branches,
       ROUND(1.0 * COUNT(c.id) / NULLIF(f.total_branches, 0), 2) AS cases_per_branch
FROM function_info f
LEFT JOIN test_case c ON c.function_id = f.id
GROUP BY f.id
ORDER BY cases DESC;

-- Q8. Тест-кейсы, ожидающие исключение
SELECT f.function_name, c.code, c.expected_result
FROM test_case c
JOIN function_info f ON f.id = c.function_id
WHERE c.expected_result LIKE '%Exception%'
ORDER BY f.function_name, c.code;

-- Q9. Входные значения для граничных кейсов одной функции
SELECT c.code, GROUP_CONCAT(i.param_name || '=' || COALESCE(i.param_value, 'null'), ', ') AS inputs
FROM test_case c
JOIN function_info f ON f.id = c.function_id
JOIN scenario_type t ON t.id = c.scenario_type_id
JOIN test_input i ON i.test_case_id = c.id
WHERE f.function_name = 'calculateDiscount' AND t.name = 'BOUNDARY'
GROUP BY c.id
ORDER BY c.id
LIMIT 10;

-- Q10. Файлы проектов и число функций в каждом
SELECT p.name AS project, s.file_name, COUNT(DISTINCT f.function_name) AS functions
FROM source_file s
JOIN project p ON p.id = s.project_id
LEFT JOIN function_info f ON f.source_file_id = s.id
GROUP BY s.id
ORDER BY p.name, s.file_name;

-- Q11. Артефакты запусков
SELECT r.id, a.kind, a.path
FROM artifact a
JOIN run r ON r.id = a.run_id
ORDER BY r.started_at, a.kind;

-- Q12. Представление со сводкой запусков
SELECT * FROM v_run_summary ORDER BY started_at;
