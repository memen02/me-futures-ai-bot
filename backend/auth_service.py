"""Service facade for a future authenticated HTTPS adapter; not a listener."""
from auth_accounts import normalize, register
from auth_sessions import login, review, revoke, actor
from auth_db import transaction
from auth_entitlements import grant, revoke_license
from auth_security import permit
from auth_verified_login import verified_login
from auth_devices import bind
from auth_device_challenges import issue
from auth_accounts import verify_password
from auth_entitlements import licensed
import time

class AuthService:
    def __init__(self,path):
        self.path=path

    def register(self,username,password):
        if not permit(self.path,"register_global","all",limit=20):
            return "UNAVAILABLE"
        try:
            register(self.path,username,password)
            # Generic response avoids explicitly disclosing existing usernames.
            return "ACCEPTED"
        except (ValueError,TypeError):
            return "REJECTED"

    def login(self,username,password,proof=None):
        if not permit(self.path,"login_global","all",limit=60):
            return None
        try:
            username=normalize(username)
            if not permit(self.path,"login_user",username):
                return None
            if proof is not None:
                if not isinstance(proof,dict) or set(proof) != {"key_digest","nonce","public_der","signature"}:
                    return None
                return verified_login(self.path,username,password,**proof)
            with transaction(self.path) as db:
                owner = db.execute("SELECT 1 FROM accounts WHERE username=? AND role='SUPER_ADMIN'",(username,)).fetchone()
            return login(self.path,username,password) if owner else None
        except (ValueError,TypeError):
            return None

    def review(self,token,target,expected,desired):
        if not permit(self.path,"admin_global","all",limit=60):
            return "UNAVAILABLE"
        try:
            return review(self.path,token,target,expected,desired)
        except (ValueError,TypeError):
            return "DENIED"

    def logout(self,token):
        try:
            revoke(self.path,token)
        except (ValueError,TypeError):
            return None

    def authorize(self,token):
        try:
            with transaction(self.path) as db:
                row = actor(db,token)
                return dict(row) if row is not None else None
        except (ValueError,TypeError):
            return None

    def grant_license(self,token,target,kind,expires=None):
        if not permit(self.path,"admin_global","all",limit=60):
            return "UNAVAILABLE"
        try:
            return grant(self.path,token,target,kind,expires)
        except (ValueError,TypeError):
            return "DENIED"

    def revoke_license(self,token,target):
        if not permit(self.path,"admin_global","all",limit=60):
            return "UNAVAILABLE"
        try:
            return revoke_license(self.path,token,target)
        except (ValueError,TypeError):
            return "DENIED"

    def device_challenge(self,username,password,key_digest):
        if not permit(self.path,"login_global","all",limit=60):
            return None
        try:
            username = normalize(username)
            if not permit(self.path,"login_user",username):
                return None
            with transaction(self.path) as db:
                row = db.execute("SELECT * FROM accounts WHERE username=? AND role='USER' AND status='APPROVED'",(username,)).fetchone()
                if row is None or not verify_password(password,row["password_hash"]) or not licensed(db,row["id"],int(time.time())):
                    return None
                account_id = row["id"]
            nonce = issue(self.path,account_id,key_digest)
            return {"accountId":account_id,"nonce":nonce} if nonce else None
        except (ValueError,TypeError):
            return None

    def bind_device(self,token,target,key_digest,expected=None):
        if not permit(self.path,"admin_global","all",limit=60):
            return "UNAVAILABLE"
        try:
            return bind(self.path,token,target,key_digest,expected)
        except (ValueError,TypeError):
            return "DENIED"
