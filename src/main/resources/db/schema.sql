-- История запусков генерации тестов агентом IntelligentTestAgent (SQLite).

CREATE TABLE IF NOT EXISTS scenario_type (
    id   INTEGER PRIMARY KEY,
    name TEXT NOT NULL UNIQUE CHECK (name IN ('POSITIVE', 'NEGATIVE', 'BOUNDARY'))
);

CREATE TABLE IF NOT EXISTS case_origin (
    id   INTEGER PRIMARY KEY,
    name TEXT NOT NULL UNIQUE CHECK (name IN ('HEURISTIC', 'BOUNDARY', 'SEARCH'))
);

CREATE TABLE IF NOT EXISTS project (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    name       TEXT NOT NULL UNIQUE,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS source_file (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id   INTEGER NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    file_name    TEXT NOT NULL,
    package_name TEXT NOT NULL DEFAULT '',
    UNIQUE (project_id, file_name)
);

CREATE TABLE IF NOT EXISTS run (
    id                 TEXT PRIMARY KEY,
    project_id         INTEGER NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    source_path        TEXT NOT NULL,
    started_at         TEXT NOT NULL,
    duration_ms        INTEGER NOT NULL CHECK (duration_ms >= 0),
    status             TEXT NOT NULL CHECK (status IN ('COMPLETED', 'FAILED')),
    coverage_measured  INTEGER NOT NULL DEFAULT 0 CHECK (coverage_measured IN (0, 1)),
    message            TEXT
);

CREATE TABLE IF NOT EXISTS function_info (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    run_id           TEXT NOT NULL REFERENCES run (id) ON DELETE CASCADE,
    source_file_id   INTEGER REFERENCES source_file (id) ON DELETE SET NULL,
    class_name       TEXT NOT NULL,
    function_name    TEXT NOT NULL,
    signature        TEXT NOT NULL DEFAULT '',
    complexity       INTEGER CHECK (complexity IS NULL OR complexity >= 1),
    total_branches   INTEGER NOT NULL DEFAULT 0 CHECK (total_branches >= 0),
    covered_branches INTEGER NOT NULL DEFAULT 0 CHECK (covered_branches >= 0),
    UNIQUE (run_id, class_name, function_name, signature),
    CHECK (covered_branches <= total_branches)
);

CREATE TABLE IF NOT EXISTS branch (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    function_id INTEGER NOT NULL REFERENCES function_info (id) ON DELETE CASCADE,
    code        TEXT NOT NULL,
    label       TEXT NOT NULL,
    state       TEXT NOT NULL CHECK (state IN ('COVERED', 'NOT_FOUND', 'NOT_INSTRUMENTABLE')),
    UNIQUE (function_id, code)
);

CREATE TABLE IF NOT EXISTS test_case (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    function_id      INTEGER NOT NULL REFERENCES function_info (id) ON DELETE CASCADE,
    code             TEXT NOT NULL,
    scenario_type_id INTEGER NOT NULL REFERENCES scenario_type (id),
    origin_id        INTEGER NOT NULL REFERENCES case_origin (id),
    description      TEXT NOT NULL,
    expected_result  TEXT,
    UNIQUE (function_id, code)
);

CREATE TABLE IF NOT EXISTS test_input (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    test_case_id  INTEGER NOT NULL REFERENCES test_case (id) ON DELETE CASCADE,
    param_name    TEXT NOT NULL,
    param_value   TEXT,
    UNIQUE (test_case_id, param_name)
);

CREATE TABLE IF NOT EXISTS artifact (
    id      INTEGER PRIMARY KEY AUTOINCREMENT,
    run_id  TEXT NOT NULL REFERENCES run (id) ON DELETE CASCADE,
    kind    TEXT NOT NULL CHECK (kind IN ('ANALYSIS', 'TEST_CASES', 'COVERAGE', 'TESTS_SOURCE')),
    path    TEXT NOT NULL,
    UNIQUE (run_id, kind)
);

CREATE TABLE IF NOT EXISTS note (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    run_id     TEXT NOT NULL REFERENCES run (id) ON DELETE CASCADE,
    created_at TEXT NOT NULL,
    text       TEXT NOT NULL CHECK (length(trim(text)) > 0)
);

CREATE INDEX IF NOT EXISTS idx_run_project_started ON run (project_id, started_at);
CREATE INDEX IF NOT EXISTS idx_note_run ON note (run_id, created_at);
CREATE INDEX IF NOT EXISTS idx_function_run ON function_info (run_id);
CREATE INDEX IF NOT EXISTS idx_function_complexity ON function_info (complexity);
CREATE INDEX IF NOT EXISTS idx_branch_function_state ON branch (function_id, state);
CREATE INDEX IF NOT EXISTS idx_case_function ON test_case (function_id);
CREATE INDEX IF NOT EXISTS idx_case_type ON test_case (scenario_type_id);
CREATE INDEX IF NOT EXISTS idx_input_case ON test_input (test_case_id);

CREATE VIEW IF NOT EXISTS v_run_summary AS
SELECT r.id AS run_id,
       p.name AS project,
       r.started_at,
       r.status,
       COUNT(DISTINCT f.id) AS functions,
       COALESCE(SUM(f.total_branches), 0) AS total_branches,
       COALESCE(SUM(f.covered_branches), 0) AS covered_branches
FROM run r
JOIN project p ON p.id = r.project_id
LEFT JOIN function_info f ON f.run_id = r.id
GROUP BY r.id;

INSERT OR IGNORE INTO scenario_type (id, name) VALUES (1, 'POSITIVE'), (2, 'NEGATIVE'), (3, 'BOUNDARY');
INSERT OR IGNORE INTO case_origin (id, name) VALUES (1, 'HEURISTIC'), (2, 'BOUNDARY'), (3, 'SEARCH');
