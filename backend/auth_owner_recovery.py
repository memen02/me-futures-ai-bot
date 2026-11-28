"""Offline provisioning and recovery primitives; not exposed over HTTP."""
import hashlib
import secrets
import hmac
import time
import sqlite3
from auth_accounts import normalize, verify_password
from auth_db import transaction
from auth_devices import valid_digest

def provision(path, username):
    """Trusted offline operator only. Return secret once; never log it."""
    username = normalize(username)
    secret = secrets.token_hex(32)
    with transaction(path) as db:
        row = db.execute("SELECT id FROM accounts WHERE username=? AND role='SUPER_ADMIN' AND status='APPROVED'", (username,)).fetchone()
        if row is None:
            raise ValueError("owner unavailable")
        db.execute("INSERT INTO owner_recovery VALUES(?,?) ON CONFLICT(account_id) DO UPDATE SET digest=excluded.digest",
                   (row["id"], hashlib.sha256(secret.encode("ascii")).hexdigest()))
    return secret
