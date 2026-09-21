from pathlib import Path
import sqlite3

root = Path(__file__).resolve().parents[1]
db_path = root / "lab11-database" / "intelligent_test_agent.db"
queries = [q.strip() for q in (root / "lab11-database" / "queries.sql").read_text(encoding="utf-8").split(";") if q.strip()]
with sqlite3.connect(db_path) as con:
    con.execute("PRAGMA foreign_keys=ON")
    integrity = con.execute("PRAGMA integrity_check").fetchone()[0]
    fk_errors = con.execute("PRAGMA foreign_key_check").fetchall()
    print("integrity_check:", integrity)
    print("foreign_key_errors:", len(fk_errors))
    for i, query in enumerate(queries, 1):
        rows = con.execute(query).fetchall()
        print(f"query_{i}_rows:", len(rows))
        for row in rows: print(" ", row)
    assert integrity == "ok" and not fk_errors
