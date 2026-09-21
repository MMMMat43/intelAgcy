from pathlib import Path
import sqlite3

root = Path(__file__).resolve().parents[1]
db_path = root / "lab11-database" / "intelligent_test_agent.db"
if db_path.exists():
    db_path.unlink()
with sqlite3.connect(db_path) as con:
    con.executescript((root / "lab11-database" / "schema.sql").read_text(encoding="utf-8"))
    con.executescript((root / "lab11-database" / "sample_data.sql").read_text(encoding="utf-8"))
    con.commit()
print(db_path)
