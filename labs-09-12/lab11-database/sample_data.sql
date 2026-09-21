PRAGMA foreign_keys = ON;
INSERT INTO projects(name,root_path) VALUES ('Order Processor','samples/OrderProcessor.java'),('Calculator','samples/SampleCalculator.java');
INSERT INTO source_files(project_id,relative_path,content_hash,line_count) VALUES (1,'OrderProcessor.java','sha256:demo-order',122),(2,'SampleCalculator.java','sha256:demo-calc',23);
INSERT INTO analysis_runs(project_id,status,finished_at,llm_model) VALUES (1,'COMPLETED',CURRENT_TIMESTAMP,'openai/gpt-oss-20b:free'),(2,'COMPLETED',CURRENT_TIMESTAMP,NULL);
INSERT INTO analyzed_methods(run_id,source_file_id,class_name,method_name,signature,cyclomatic_complexity) VALUES
(1,1,'OrderProcessor','calculateDiscount','double calculateDiscount(double,int,boolean)',7),
(1,1,'OrderProcessor','validateOrder','boolean validateOrder(int,double)',4),
(2,2,'SampleCalculator','divide','int divide(int,int)',4);
INSERT INTO test_cases(method_id,scenario_type,title,input_data,expected_result,generated_by) VALUES
(1,'POSITIVE','Обычная скидка участника','{"price":100,"quantity":2,"isMember":true}','30.0','HEURISTIC'),
(1,'BOUNDARY','Нулевая цена','{"price":0,"quantity":1,"isMember":true}','0.0','HEURISTIC'),
(2,'NEGATIVE','Количество меньше нуля','{"quantity":-1,"price":10}','IllegalArgumentException','HEURISTIC'),
(3,'NEGATIVE','Деление на ноль','{"numerator":1,"denominator":0}','ArithmeticException','HEURISTIC');
INSERT INTO generated_artifacts(run_id,artifact_type,file_path) VALUES
(1,'ANALYSIS_JSON','output/order/analysis.json'),(1,'TEST_CASES_JSON','output/order/test-cases.json'),(1,'JUNIT5_SOURCE','output/order/GeneratedTests.kt');
