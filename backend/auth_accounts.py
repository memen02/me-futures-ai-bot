"""Account primitives; expose only through a rate-limited trusted service."""
import hashlib
import hmac
import re
import secrets
import sqlite3
from auth_db import transaction

def normalize(username):
    if not isinstance(username, str) or not re.fullmatch(r"[A-Za-z0-9_]{4,32}", username):
        raise ValueError("invalid username")
    return username.lower()

def hash_password(password):
    if not isinstance(password, str) or not 12 <= len(password) <= 128 or not password.strip() or any(ord(c)<32 or 127<=ord(c)<=159 for c in password):
        raise ValueError("invalid password")
    salt = secrets.token_bytes(16)
    digest = hashlib.scrypt(password.encode("utf-8"), salt=salt, n=32768, r=8, p=3, maxmem=64*1024*1024)
    return salt.hex()+":"+digest.hex()

def verify_password(password, encoded):
    if not isinstance(password,str) or not 1<=len(password)<=128:
        return False
    try:
        if not isinstance(encoded, str) or not re.fullmatch(r"[0-9a-f]{32}:[0-9a-f]{128}", encoded):
            return False
        salt, digest = map(bytes.fromhex, encoded.split(":"))
        if len(salt)!=16 or len(digest)!=64:
            return False
        actual = hashlib.scrypt(password.encode("utf-8"), salt=salt,n=32768,r=8,p=3,maxmem=64*1024*1024)
        return hmac.compare_digest(actual,digest)
    except (ValueError, TypeError):
        return False

def register(path, username, password):
    username = normalize(username)
    encoded = hash_password(password)
    account_id = secrets.token_hex(16)
    try:
        with transaction(path) as db:
            db.execute("INSERT INTO accounts VALUES(?,?,?,'USER','PENDING')",
                       (account_id,username,encoded))
        return True
    except sqlite3.IntegrityError:
        return False
