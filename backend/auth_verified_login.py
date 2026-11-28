"""Device-verified USER login; legacy login must be retired before HTTP exposure."""
import secrets
import time
from auth_accounts import normalize, verify_password
from auth_db import transaction
from auth_entitlements import licensed
from auth_sessions import digest
from auth_device_proof import verify_device_proof
from auth_device_challenges import consume

def verified_login(path, username, password, key_digest, nonce, public_der, signature):
    username = normalize(username)
    with transaction(path) as db:
        row = db.execute("SELECT * FROM accounts WHERE username=?", (username,)).fetchone()
        if row is None or row["role"] != "USER" or row["status"] != "APPROVED":
            return None
        if not verify_password(password,row["password_hash"]):
            return None
        if not verify_device_proof(row["id"],key_digest,nonce,public_der,signature):
            return None
        now = int(time.time())
        if not licensed(db,row["id"],now) or not consume(db,row["id"],key_digest,nonce,now):
            return None
        token = secrets.token_hex(32)
        db.execute("DELETE FROM sessions WHERE expires<=? OR account_id=?", (now,row["id"]))
        db.execute("INSERT INTO sessions VALUES(?,?,?)",(digest(token),row["id"],now+900))
        return {"accountId":row["id"],"username":row["username"],"role":row["role"],
                "status":row["status"],"accessToken":token,"lifetimeSeconds":900}
