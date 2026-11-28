"""Device enrollment metadata only; not proof of possession or a login gate."""
import re
import sqlite3
import time
from auth_db import transaction
from auth_sessions import actor

def valid_digest(value):
    return isinstance(value, str) and re.fullmatch(r"[0-9a-f]{64}", value) is not None

def bind(path, token, target, new_digest, expected=None):
    if not valid_digest(new_digest) or (expected is not None and not valid_digest(expected)):
        return "DENIED"
    with transaction(path) as db:
        admin = actor(db, token)
        if admin is None or admin["role"] != "SUPER_ADMIN":
            return "DENIED"
        if db.execute("SELECT 1 FROM accounts WHERE id=? AND role='USER' AND status='APPROVED'", (target,)).fetchone() is None:
            return "DENIED"
        current = db.execute("SELECT public_key_digest FROM device_bindings WHERE account_id=?", (target,)).fetchone()
        if (current[0] if current else None) != expected:
            return "CONFLICT"
        if expected == new_digest:
            return "CONFLICT"
        try:
            db.execute("""INSERT INTO device_bindings(account_id,public_key_digest) VALUES(?,?)
                ON CONFLICT(account_id) DO UPDATE SET public_key_digest=excluded.public_key_digest""",
                (target,new_digest))
        except sqlite3.IntegrityError:
            return "CONFLICT"
        db.execute("DELETE FROM sessions WHERE account_id=?", (target,))
        db.execute("INSERT INTO admin_audit(actor,target,action,created) VALUES(?,?,?,?)",
                   (admin["id"],target,"DEVICE_REPLACED" if current else "DEVICE_BOUND",int(time.time())))
        return "APPLIED"
