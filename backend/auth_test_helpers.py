"""Test-only helper; never imported by production authentication."""
import hashlib
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from auth_db import transaction
from auth_devices import bind
from auth_device_challenges import issue_authenticated
from auth_verified_login import verified_login

def user_session(path, admin, username, password):
    with transaction(path) as db:
        account = db.execute("SELECT id FROM accounts WHERE username=?",(username,)).fetchone()[0]
        current = db.execute("SELECT public_key_digest FROM device_bindings WHERE account_id=?",(account,)).fetchone()
    key = ec.generate_private_key(ec.SECP256R1())
    der = key.public_key().public_bytes(serialization.Encoding.DER,serialization.PublicFormat.SubjectPublicKeyInfo)
    digest = hashlib.sha256(der).hexdigest()
    assert bind(path,admin,account,digest,current[0] if current else None) == "APPLIED"
    challenge = issue_authenticated(path,username,password,digest)
    assert challenge is not None
    nonce = challenge["nonce"]
    message = ("ME-PROJECT-DEVICE-LOGIN-V1:"+account+":"+digest+":"+nonce).encode("ascii")
    return verified_login(path,username,password,digest,nonce,der,key.sign(message,ec.ECDSA(hashes.SHA256())))
