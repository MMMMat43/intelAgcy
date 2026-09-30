#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Пересоздаёт базу agent_history.db.

Схема берётся из src/main/resources/db/schema.sql (единый источник для агента и
для лабораторной). Данные загружаются из РЕАЛЬНЫХ результатов запуска агента
(analysis.json, test-cases.json, coverage.json), каталог передаётся аргументом.

Запуск:  py create_database.py --runs <каталог с подкаталогами запусков> [--db agent_history.db]
Каждый подкаталог запуска содержит analysis.json, test-cases.json, coverage.json.
"""
import argparse
import datetime
import json
import os
import re
import sqlite3
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
SCHEMA = os.path.join(ROOT, "src", "main", "resources", "db", "schema.sql")

ORIGINS = {"HEURISTIC", "BOUNDARY", "SEARCH"}


def read_json(path):
    with open(path, encoding="utf-8-sig") as handle:
        return json.load(handle)


def origin_of(case):
    value = case.get("origin") or "SEARCH"
    return value if value in ORIGINS else "SEARCH"


def load_run(connection, run_dir, started_at, duration_ms, ordinal):
    analysis = read_json(os.path.join(run_dir, "analysis.json"))
    cases = read_json(os.path.join(run_dir, "test-cases.json"))
    coverage = read_json(os.path.join(run_dir, "coverage.json"))

    source_path = analysis.get("sourcePath", run_dir)
    project_name = os.path.splitext(os.path.basename(source_path.replace("\\", "/")))[0] or "unnamed"
    measured = 1 if coverage.get("measured", True) else 0

    connection.execute("INSERT OR IGNORE INTO project (name, created_at) VALUES (?, ?)", (project_name, started_at))
    project_id = connection.execute("SELECT id FROM project WHERE name = ?", (project_name,)).fetchone()[0]

    run_id = "run-%s-%02d" % (project_name.lower(), ordinal)
    connection.execute(
        "INSERT INTO run (id, project_id, source_path, started_at, duration_ms, status, coverage_measured, message) "
        "VALUES (?, ?, ?, ?, ?, 'COMPLETED', ?, NULL)",
        (run_id, project_id, source_path, started_at, duration_ms, measured),
    )

    file_name = os.path.basename(source_path.replace("\\", "/"))
    package = ""
    for fn in analysis.get("functions", []):
        package = fn.get("packageName") or package
    connection.execute(
        "INSERT OR IGNORE INTO source_file (project_id, file_name, package_name) VALUES (?, ?, ?)",
        (project_id, file_name, package),
    )
    file_id = connection.execute(
        "SELECT id FROM source_file WHERE project_id = ? AND file_name = ?", (project_id, file_name)
    ).fetchone()[0]

    coverage_by_key = {}
    for fn in coverage.get("functions", []):
        key = "%s.%s" % (fn.get("className"), fn.get("functionName"))
        coverage_by_key.setdefault(key, fn)

    function_ids = {}
    skipped = {s.get("name") for s in analysis.get("skippedFunctions", [])} if isinstance(analysis.get("skippedFunctions"), list) else set()
    seen = set()
    for fn in analysis.get("functions", []):
        if fn.get("skipReason"):
            continue
        key = "%s.%s" % (fn.get("className"), fn.get("name"))
        params = ", ".join("%s: %s" % (p["name"], p["type"]) for p in fn.get("parameters", []))
        unique = (key, params)
        if unique in seen:
            continue
        seen.add(unique)
        cov = coverage_by_key.get(key, {})
        total = int(cov.get("totalBranches", 0))
        covered = int(cov.get("coveredBranches", 0))
        cursor = connection.execute(
            "INSERT INTO function_info (run_id, source_file_id, class_name, function_name, signature, complexity, "
            "total_branches, covered_branches) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            (run_id, file_id, fn.get("className"), fn.get("name"), params,
             fn.get("cyclomaticComplexity"), total, covered),
        )
        function_ids[key] = cursor.lastrowid
        for state_key, state in (("covered", "COVERED"), ("notFoundWithinBudget", "NOT_FOUND"), ("notInstrumentable", "NOT_INSTRUMENTABLE")):
            for branch in cov.get(state_key, []):
                connection.execute(
                    "INSERT OR IGNORE INTO branch (function_id, code, label, state) VALUES (?, ?, ?, ?)",
                    (cursor.lastrowid, branch.get("id"), branch.get("label", ""), state),
                )

    items = cases.get("testCases", cases if isinstance(cases, list) else [])
    for case in items:
        key = "%s.%s" % (case.get("className"), case.get("functionName"))
        function_id = function_ids.get(key)
        if function_id is None:
            continue
        cursor = connection.execute(
            "INSERT OR IGNORE INTO test_case (function_id, code, scenario_type_id, origin_id, description, expected_result) "
            "VALUES (?, ?, (SELECT id FROM scenario_type WHERE name = ?), (SELECT id FROM case_origin WHERE name = ?), ?, ?)",
            (function_id, case.get("id"), case.get("type"), origin_of(case), case.get("description", ""), case.get("expectedResult")),
        )
        if cursor.rowcount == 0:
            continue
        for name, value in (case.get("inputData") or {}).items():
            connection.execute(
                "INSERT OR IGNORE INTO test_input (test_case_id, param_name, param_value) VALUES (?, ?, ?)",
                (cursor.lastrowid, name, value),
            )

    for kind, rel in (("ANALYSIS", "analysis.json"), ("TEST_CASES", "test-cases.json"), ("COVERAGE", "coverage.json"),
                      ("TESTS_SOURCE", "generated-tests")):
        connection.execute(
            "INSERT OR REPLACE INTO artifact (run_id, kind, path) VALUES (?, ?, ?)",
            (run_id, kind, "runs/%s/%s" % (os.path.basename(run_dir), rel)),
        )
    return run_id


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--runs", required=True)
    parser.add_argument("--db", default=os.path.join(HERE, "agent_history.db"))
    parser.add_argument("--seed-out", default=os.path.join(HERE, "seed.sql"))
    args = parser.parse_args()

    if os.path.exists(args.db):
        os.remove(args.db)
    connection = sqlite3.connect(args.db)
    connection.execute("PRAGMA foreign_keys = ON")
    with open(SCHEMA, encoding="utf-8") as handle:
        connection.executescript(handle.read())

    base = datetime.datetime(2026, 7, 1, 9, 0, 0, tzinfo=datetime.timezone.utc)
    run_dirs = sorted(d for d in os.listdir(args.runs) if os.path.isfile(os.path.join(args.runs, d, "analysis.json")))
    for index, name in enumerate(run_dirs, 1):
        started = (base + datetime.timedelta(hours=index)).strftime("%Y-%m-%dT%H:%M:%SZ")
        load_run(connection, os.path.join(args.runs, name), started, 1500 * index, index)
    connection.commit()

    with open(args.seed_out, "w", encoding="utf-8", newline="\n") as handle:
        handle.write("-- Данные получены из реальных запусков агента (analysis.json, test-cases.json, coverage.json).\n")
        handle.write("PRAGMA foreign_keys = ON;\nBEGIN TRANSACTION;\n")
        for line in connection.iterdump():
            if line.startswith("INSERT INTO") and not line.startswith(("INSERT INTO sqlite_sequence", "INSERT INTO \"sqlite_sequence\"")):
                handle.write(line + "\n")
        handle.write("COMMIT;\n")
    connection.close()
    print("База создана:", args.db)


if __name__ == "__main__":
    sys.exit(main())
