#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Проверка базы: целостность, внешние ключи, число строк в таблицах, выполнение всех запросов."""
import os
import re
import sqlite3
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
db = os.path.join(HERE, "agent_history.db")
connection = sqlite3.connect(db)
connection.execute("PRAGMA foreign_keys = ON")

ok = True
integrity = connection.execute("PRAGMA integrity_check").fetchone()[0]
print("integrity_check:", integrity)
ok &= integrity == "ok"
violations = connection.execute("PRAGMA foreign_key_check").fetchall()
print("foreign_key_check: нарушений", len(violations))
ok &= not violations

print("\nТаблицы и число строк:")
tables = [r[0] for r in connection.execute(
    "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name")]
for table in tables:
    count = connection.execute('SELECT COUNT(*) FROM "%s"' % table).fetchone()[0]
    print("  %-14s %d" % (table, count))

with open(os.path.join(HERE, "queries.sql"), encoding="utf-8") as handle:
    text = handle.read()
queries = [q.strip() for q in text.split(";") if re.search(r"\bSELECT\b", re.sub(r"--[^\n]*", "", q))]
print("\nЗапросы:")
for index, query in enumerate(queries, 1):
    title = re.search(r"--\s*(Q\d+\.[^\n]*)", query)
    rows = connection.execute(re.sub(r"--[^\n]*\n", "\n", query)).fetchall()
    print("  %-70s строк: %d" % ((title.group(1) if title else "Q%d" % index)[:70], len(rows)))
    if not rows and "Q3." not in query and "Q4." not in query:
        ok = False

print("\nРезультат:", "OK" if ok else "ОШИБКА")
sys.exit(0 if ok else 1)
