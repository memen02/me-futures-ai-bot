"""Challenge storage foundation; not yet connected to signature verification or login."""
import secrets
import time
from auth_db import transaction
from auth_devices import valid_digest

def initialize(path):
    with transaction(path) as db:
        db.execute("""CREATE TABLE IF NOT EXISTS device_challenges(
            nonce TEXT PRIMARY KEY,
            account_id TEXT NOT NULL UNIQUE REFERENCES accounts(id),
            key_digest TEXT NOT NULL,
            expires INTEGER NOT NULL
        )""")

def issue(path, account_id, key_digest):
    # Internal primitive: caller must authenticate and rate-limit before issuing.
    if not valid_digest(key_digest):
        return None
    with transaction(path) as db:
        now = int(time.time())
        db.execute("DELETE FROM device_challenges WHERE expires<=?", (now,))
        if db.execute("""SELECT 1 FROM device_bindings b JOIN accounts a ON a.id=b.account_id
            WHERE b.account_id=? AND b.public_key_digest=? AND a.status='APPROVED'""",
            (account_id,key_digest)).fetchone() is None:
            return None
        nonce = secrets.token_hex(32)
        db.execute("""INSERT INTO device_challenges VALUES(?,?,?,?)
            ON CONFLICT(account_id) DO UPDATE SET nonce=excluded.nonce,
            key_digest=excluded.key_digest,expires=excluded.expires""",
            (nonce,account_id,key_digest,now+60))
        return nonce

def consume(db, account_id, key_digest, nonce, now):
    # Call only after verifying the signature, in the same transaction as authorization.
    if not valid_digest(key_digest) or not valid_digest(nonce):
        return False
    return db.execute("""DELETE FROM device_challenges
        WHERE account_id=? AND key_digest=? AND nonce=? AND expires>?
        AND EXISTS (SELECT 1 FROM device_bindings b JOIN accounts a ON a.id=b.account_id
            WHERE b.account_id=? AND b.public_key_digest=? AND a.status='APPROVED')""",
        (account_id,key_digest,nonce,now,account_id,key_digest)).rowcount == 1
