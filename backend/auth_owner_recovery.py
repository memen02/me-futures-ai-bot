"""Offline provisioning and recovery primitives; not exposed over HTTP."""
import hashlib
import secrets
import hmac
import time
import sqlite3
from auth_accounts import normalize, verify_password
from auth_db import transaction
from auth_devices import valid_digest
from auth_security import permit
from auth_device_proof import verify_recovery_proof

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
        db.execute("DELETE FROM owner_recovery_challenges WHERE account_id=?", (row["id"],))
    return secret

def recover(path, username, password, secret, new_digest, *, nonce=None, public_der=None, signature=None):
    """Trusted offline recovery; device proof is mandatory."""
    if not permit(path,"owner_recovery","all",limit=5,window=300):
        return False
    if not valid_digest(secret) or not valid_digest(new_digest):
        return False
    try:
        username = normalize(username)
        with transaction(path) as db:
            row = db.execute("""SELECT a.*,r.digest AS recovery_digest FROM accounts a
                JOIN owner_recovery r ON r.account_id=a.id
                WHERE a.username=? AND a.role='SUPER_ADMIN' AND a.status='APPROVED'""",(username,)).fetchone()
            if row is None or not hmac.compare_digest(row["recovery_digest"],hashlib.sha256(secret.encode("ascii")).hexdigest()):
                return False
            if not verify_password(password,row["password_hash"]):
                return False
            account = row["id"]
            challenge = db.execute("SELECT * FROM owner_recovery_challenges WHERE account_id=?", (account,)).fetchone()
            if challenge is None or challenge["expires"] <= int(time.time()) or challenge["nonce"] != nonce or challenge["key_digest"] != new_digest:
                return False
            if not verify_recovery_proof(account,new_digest,nonce,public_der,signature):
                return False
            db.execute("DELETE FROM owner_recovery_challenges WHERE account_id=?", (account,))
            db.execute("""INSERT INTO device_bindings VALUES(?,?)
                ON CONFLICT(account_id) DO UPDATE SET public_key_digest=excluded.public_key_digest""",(account,new_digest))
            db.execute("DELETE FROM sessions WHERE account_id=?",(account,))
            db.execute("DELETE FROM device_challenges WHERE account_id=?",(account,))
            db.execute("DELETE FROM owner_recovery WHERE account_id=?",(account,))
            db.execute("INSERT INTO admin_audit(actor,target,action,created) VALUES(?,?,?,?)",
                       (account,account,"OWNER_DEVICE_RECOVERED",int(time.time())))
            return True
    except (ValueError,TypeError,sqlite3.IntegrityError):
        return False

def issue_recovery_challenge(path, username, new_digest):
    """Trusted offline operator only."""
    if not valid_digest(new_digest):
        return None
    username = normalize(username)
    with transaction(path) as db:
        row = db.execute("""SELECT a.id FROM accounts a JOIN owner_recovery r ON r.account_id=a.id
            WHERE a.username=? AND a.role='SUPER_ADMIN' AND a.status='APPROVED'""",(username,)).fetchone()
        if row is None:
            return None
        nonce = secrets.token_hex(32)
        db.execute("""INSERT INTO owner_recovery_challenges VALUES(?,?,?,?)
            ON CONFLICT(account_id) DO UPDATE SET nonce=excluded.nonce,
            key_digest=excluded.key_digest,expires=excluded.expires""",
            (row["id"],nonce,new_digest,int(time.time())+60))
        return {"accountId":row["id"],"nonce":nonce}
