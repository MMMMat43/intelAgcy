PRAGMA foreign_keys = ON;
CREATE TABLE IF NOT EXISTS projects (
 id INTEGER PRIMARY KEY AUTOINCREMENT,
 name TEXT NOT NULL UNIQUE,
 root_path TEXT NOT NULL,
 language TEXT NOT NULL DEFAULT 'JAVA' CHECK(language IN ('JAVA')),
 created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS source_files (
 id INTEGER PRIMARY KEY AUTOINCREMENT,
 project_id INTEGER NOT NULL,
 relative_path TEXT NOT NULL,
 content_hash TEXT NOT NULL,
 line_count INTEGER NOT NULL CHECK(line_count >= 0),
 FOREIGN KEY(project_id) REFERENCES projects(id) ON DELETE CASCADE,
 UNIQUE(project_id, relative_path)
);
CREATE TABLE IF NOT EXISTS analysis_runs (
 id INTEGER PRIMARY KEY AUTOINCREMENT,
 project_id INTEGER NOT NULL,
 status TEXT NOT NULL CHECK(status IN ('CREATED','RUNNING','COMPLETED','FAILED')),
 started_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
 finished_at TEXT,
 llm_model TEXT,
 FOREIGN KEY(project_id) REFERENCES projects(id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS analyzed_methods (
 id INTEGER PRIMARY KEY AUTOINCREMENT,
 run_id INTEGER NOT NULL,
 source_file_id INTEGER NOT NULL,
 class_name TEXT NOT NULL,
 method_name TEXT NOT NULL,
 signature TEXT NOT NULL,
 cyclomatic_complexity INTEGER NOT NULL CHECK(cyclomatic_complexity >= 1),
 FOREIGN KEY(run_id) REFERENCES analysis_runs(id) ON DELETE CASCADE,
 FOREIGN KEY(source_file_id) REFERENCES source_files(id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS test_cases (
 id INTEGER PRIMARY KEY AUTOINCREMENT,
 method_id INTEGER NOT NULL,
 scenario_type TEXT NOT NULL CHECK(scenario_type IN ('POSITIVE','NEGATIVE','BOUNDARY','LLM')),
 title TEXT NOT NULL,
 input_data TEXT NOT NULL DEFAULT '{}',
 expected_result TEXT,
 generated_by TEXT NOT NULL CHECK(generated_by IN ('HEURISTIC','LLM','USER')),
 FOREIGN KEY(method_id) REFERENCES analyzed_methods(id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS generated_artifacts (
 id INTEGER PRIMARY KEY AUTOINCREMENT,
 run_id INTEGER NOT NULL,
 artifact_type TEXT NOT NULL CHECK(artifact_type IN ('ANALYSIS_JSON','TEST_CASES_JSON','JUNIT5_SOURCE')),
 file_path TEXT NOT NULL,
 created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(run_id) REFERENCES analysis_runs(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_source_files_project ON source_files(project_id);
CREATE INDEX IF NOT EXISTS idx_runs_project_status ON analysis_runs(project_id,status);
CREATE INDEX IF NOT EXISTS idx_methods_run ON analyzed_methods(run_id);
CREATE INDEX IF NOT EXISTS idx_test_cases_method_type ON test_cases(method_id,scenario_type);
CREATE INDEX IF NOT EXISTS idx_artifacts_run ON generated_artifacts(run_id);
