"""Server-side session and admin authorization primitives, no HTTP exposure."""
import hashlib
import secrets
import time
from auth_db import transaction
from auth_accounts import normalize, verify_password

def digest(token):
    if not isinstance(token,str) or len(token)!=64 or any(c not in "0123456789abcdef" for c in token):
        raise ValueError("invalid token")
    return hashlib.sha256(token.encode("ascii")).hexdigest()

def login(path, username, password):
    username = normalize(username)
    with transaction(path) as db:
        row = db.execute("SELECT * FROM accounts WHERE username=?", (username,)).fetchone()
        if row is None or not verify_password(password,row["password_hash"]) or row["status"]!="APPROVED":
            return None
        token = secrets.token_hex(32)
        now = int(time.time())
        if row["role"] != "SUPER_ADMIN":
            from auth_entitlements import licensed
            if not licensed(db, row["id"], now):
                return None
        db.execute("DELETE FROM sessions WHERE expires<=?",(now,))
        db.execute("INSERT INTO sessions VALUES(?,?,?)",(digest(token),row["id"],now+900))
        return {"accountId":row["id"],"username":row["username"],"role":row["role"],
                "status":row["status"],"accessToken":token,"lifetimeSeconds":900}

def actor(db, token):
    now = int(time.time())
    return db.execute("""SELECT a.id,a.username,a.role,a.status FROM sessions s
        JOIN accounts a ON a.id=s.account_id
        WHERE s.digest=? AND s.expires>? AND a.status='APPROVED'
        AND (a.role='SUPER_ADMIN' OR EXISTS (SELECT 1 FROM entitlements e
            WHERE e.account_id=a.id AND (e.kind='FAMILY' OR e.expires>?)))""",
        (digest(token),now,now)).fetchone()

def revoke(path,token):
    with transaction(path) as db:
        db.execute("DELETE FROM sessions WHERE digest=?",(digest(token),))

def review(path,token,target,expected,desired):
    allowed = {("PENDING","APPROVED"),("PENDING","REJECTED"),
               ("APPROVED","SUSPENDED"),("SUSPENDED","APPROVED")}
    with transaction(path) as db:
        admin = actor(db,token)
        if admin is None or admin["role"]!="SUPER_ADMIN" or admin["id"]==target:
            return "DENIED"
        if (expected,desired) not in allowed:
            return "DENIED"
        changed = db.execute("UPDATE accounts SET status=? WHERE id=? AND role='USER' AND status=?",
                             (desired,target,expected)).rowcount
        if changed!=1:
            return "CONFLICT"
        db.execute("DELETE FROM sessions WHERE account_id=?",(target,))
        db.execute("INSERT INTO admin_audit(actor,target,action,created) VALUES(?,?,?,?)",
                   (admin["id"],target,expected+"->"+desired,int(time.time())))
        return "APPLIED"
