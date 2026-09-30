#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Строит ER-диаграмму (PlantUML, нотация IE) по фактической структуре базы agent_history.db."""
import os
import sqlite3
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
db = os.path.join(HERE, "agent_history.db")
out = os.path.join(HERE, "er-diagram.puml")

connection = sqlite3.connect(db)
tables = [r[0] for r in connection.execute(
    "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name")]

reference = {"scenario_type": "справочник", "case_origin": "справочник"}
lines = [
    "@startuml",
    "!pragma layout smetana",
    "skinparam dpi 150",
    "skinparam shadowing false",
    "skinparam linetype ortho",
    "hide circle",
    "skinparam ranksep 50",
    "skinparam nodesep 40",
    "title ER-диаграмма базы истории запусков агента (SQLite)",
]
for table in tables:
    cols = connection.execute('PRAGMA table_info("%s")' % table).fetchall()
    fks = {row[3]: row[2] for row in connection.execute('PRAGMA foreign_key_list("%s")' % table)}
    color = " #E4DFEC" if table in reference else ""
    lines.append('entity "%s" as %s%s {' % (table, table, color))
    for cid, name, ctype, notnull, default, pk in cols:
        if pk:
            lines.append("  *%s : %s <<PK>>" % (name, ctype or "TEXT"))
    lines.append("  --")
    for cid, name, ctype, notnull, default, pk in cols:
        if pk:
            continue
        mark = "*" if notnull else ""
        tag = " <<FK>>" if name in fks else ""
        lines.append("  %s%s : %s%s" % (mark, name, ctype or "TEXT", tag))
    lines.append("}")

for table in tables:
    for row in connection.execute('PRAGMA foreign_key_list("%s")' % table):
        parent = row[2]
        child_col = row[3]
        nullable = connection.execute('PRAGMA table_info("%s")' % table).fetchall()
        is_null = any(c[1] == child_col and not c[3] for c in nullable)
        left = "|o" if is_null else "||"
        lines.append("%s %s--o{ %s : %s" % (parent, left, table, child_col))

lines.append("legend right")
lines.append("  * обязательное поле (NOT NULL)")
lines.append("  PK первичный ключ, FK внешний ключ")
lines.append("  <back:#E4DFEC>      </back> справочная таблица")
lines.append("endlegend")
lines.append("@enduml")
with open(out, "w", encoding="utf-8", newline="\n") as handle:
    handle.write("\n".join(lines) + "\n")
print("ER-диаграмма:", out, "таблиц:", len(tables))
