"""Persistent fixed-window preflight for a single trusted backend database."""
import hashlib
import time
import secrets
from auth_db import transaction
from auth_accounts import normalize, hash_password

def bootstrap_owner(path, username, password):
    """Offline operator only; never expose as an HTTP route."""
    username = normalize(username)
    encoded = hash_password(password)
    with transaction(path) as db:
        if db.execute("SELECT 1 FROM accounts WHERE role='SUPER_ADMIN'").fetchone():
            raise ValueError("owner already exists")
        db.execute("INSERT INTO accounts VALUES(?,?,?,'SUPER_ADMIN','APPROVED')",
                   (secrets.token_hex(16),username,encoded))

def permit(path, scope, subject, limit=5, window=300):
    if scope not in ("login_global","login_user","register_global","admin_global"):
        raise ValueError("invalid scope")
    if not isinstance(subject,str) or len(subject)>128 or not 1<=limit<=1000 or not 60<=window<=3600:
        raise ValueError("invalid limit input")
    now = int(time.time())
    key = hashlib.sha256((scope+":"+subject).encode()).hexdigest()
    with transaction(path) as db:
        db.execute("CREATE TABLE IF NOT EXISTS auth_limits(key TEXT PRIMARY KEY, expires INTEGER NOT NULL, count INTEGER NOT NULL)")
        db.execute("DELETE FROM auth_limits WHERE expires<=?",(now,))
        row = db.execute("SELECT count FROM auth_limits WHERE key=?",(key,)).fetchone()
        if row is None:
            db.execute("INSERT INTO auth_limits VALUES(?,?,1)",(key,now+window))
            return True
        if row["count"]>=limit:
            return False
        db.execute("UPDATE auth_limits SET count=count+1 WHERE key=?",(key,))
        return True
