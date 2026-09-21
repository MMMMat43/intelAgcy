-- Запуски и число сгенерированных тестов
SELECT p.name, r.id AS run_id, r.status, COUNT(tc.id) AS test_count
FROM projects p JOIN analysis_runs r ON r.project_id=p.id
LEFT JOIN analyzed_methods m ON m.run_id=r.id LEFT JOIN test_cases tc ON tc.method_id=m.id
GROUP BY p.name,r.id,r.status ORDER BY r.id;
-- Методы повышенной сложности
SELECT p.name,m.class_name,m.method_name,m.cyclomatic_complexity
FROM analyzed_methods m JOIN analysis_runs r ON r.id=m.run_id JOIN projects p ON p.id=r.project_id
WHERE m.cyclomatic_complexity>=4 ORDER BY m.cyclomatic_complexity DESC;
-- Артефакты завершённых запусков
SELECT p.name,a.artifact_type,a.file_path FROM generated_artifacts a
JOIN analysis_runs r ON r.id=a.run_id JOIN projects p ON p.id=r.project_id WHERE r.status='COMPLETED';
