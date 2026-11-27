"""Local backend foundation; not a deployed HTTP service."""
import sqlite3
from contextlib import contextmanager

@contextmanager
def transaction(path):
    db = sqlite3.connect(path, timeout=5, isolation_level=None)
    db.row_factory = sqlite3.Row
    try:
        db.execute("PRAGMA foreign_keys=ON")
        db.execute("BEGIN IMMEDIATE")
        yield db
        db.commit()
    except BaseException:
        db.rollback()
        raise
    finally:
        db.close()

def initialize(path):
    with transaction(path) as db:
        db.execute("""CREATE TABLE IF NOT EXISTS accounts(
            id TEXT PRIMARY KEY, username TEXT NOT NULL UNIQUE,
            password_hash TEXT NOT NULL,
            role TEXT NOT NULL CHECK(role IN ('USER','SUPER_ADMIN')),
            status TEXT NOT NULL CHECK(status IN ('PENDING','APPROVED','SUSPENDED','REJECTED'))
        )""")
        db.execute("""CREATE TABLE IF NOT EXISTS sessions(
            digest TEXT PRIMARY KEY, account_id TEXT NOT NULL REFERENCES accounts(id),
            expires INTEGER NOT NULL
        )""")
        db.execute("CREATE INDEX IF NOT EXISTS sessions_account ON sessions(account_id)")
        db.execute("""CREATE TABLE IF NOT EXISTS admin_audit(
            id INTEGER PRIMARY KEY, actor TEXT NOT NULL REFERENCES accounts(id),
            target TEXT NOT NULL REFERENCES accounts(id), action TEXT NOT NULL,
            created INTEGER NOT NULL
        )""")
