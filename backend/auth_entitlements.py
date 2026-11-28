"""Owner-managed entitlements; enforced by backend login and session checks."""
import re
import time
from auth_db import transaction
from auth_sessions import actor

def initialize_entitlements(path):
    with transaction(path) as db:
        db.execute("""CREATE TABLE IF NOT EXISTS entitlements(
            account_id TEXT PRIMARY KEY REFERENCES accounts(id),
            kind TEXT NOT NULL CHECK(kind IN ('FAMILY','MONTHLY')),
            expires INTEGER,
            CHECK((kind='FAMILY' AND expires IS NULL) OR
                  (kind='MONTHLY' AND expires IS NOT NULL))
        )""")
        db.execute("""CREATE TABLE IF NOT EXISTS device_bindings(
            account_id TEXT PRIMARY KEY REFERENCES accounts(id),
            public_key_digest TEXT NOT NULL UNIQUE
        )""")

def grant(path, token, target, kind, expires=None):
    now = int(time.time())
    if kind == "FAMILY":
        if expires is not None:
            return "DENIED"
    elif kind == "MONTHLY":
        if type(expires) is not int or not now < expires <= now + 31*86400:
            return "DENIED"
    else:
        return "DENIED"
    with transaction(path) as db:
        admin = actor(db, token)
        if admin is None or admin["role"] != "SUPER_ADMIN":
            return "DENIED"
        user = db.execute("SELECT id FROM accounts WHERE id=? AND role='USER' AND status='APPROVED'", (target,)).fetchone()
        if user is None:
            return "DENIED"
        db.execute("""INSERT INTO entitlements VALUES(?,?,?)
            ON CONFLICT(account_id) DO UPDATE SET kind=excluded.kind, expires=excluded.expires""",
            (target,kind,expires))
        db.execute("DELETE FROM device_challenges WHERE account_id=?", (target,))
        db.execute("DELETE FROM sessions WHERE account_id=?", (target,))
        db.execute("INSERT INTO admin_audit(actor,target,action,created) VALUES(?,?,?,?)",
                   (admin["id"],target,"LICENSE_"+kind,now))
        return "APPLIED"

def revoke_license(path, token, target):
    with transaction(path) as db:
        admin = actor(db, token)
        if admin is None or admin["role"] != "SUPER_ADMIN":
            return "DENIED"
        if db.execute("DELETE FROM entitlements WHERE account_id=?", (target,)).rowcount != 1:
            return "CONFLICT"
        db.execute("DELETE FROM device_challenges WHERE account_id=?", (target,))
        db.execute("DELETE FROM sessions WHERE account_id=?", (target,))
        db.execute("INSERT INTO admin_audit(actor,target,action,created) VALUES(?,?,?,?)",
                   (admin["id"],target,"LICENSE_REVOKED",int(time.time())))
        return "APPLIED"

def licensed(db, account_id, now):
    return db.execute("""SELECT 1 FROM entitlements WHERE account_id=?
        AND (kind='FAMILY' OR expires>?)""", (account_id,now)).fetchone() is not None
