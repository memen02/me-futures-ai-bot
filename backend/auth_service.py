"""Service facade for a future authenticated HTTPS adapter; not a listener."""
from auth_accounts import normalize, register
from auth_sessions import owner_login, review, revoke, actor
from auth_db import transaction
from auth_entitlements import grant, revoke_license
from auth_security import permit
from auth_verified_login import verified_login
from auth_devices import bind
from auth_device_challenges import issue_authenticated

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
            return owner_login(self.path,username,password)
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
            return issue_authenticated(self.path,username,password,key_digest)
        except (ValueError,TypeError):
            return None

    def bind_device(self,token,target,key_digest,expected=None):
        if not permit(self.path,"admin_global","all",limit=60):
            return "UNAVAILABLE"
        try:
            return bind(self.path,token,target,key_digest,expected)
        except (ValueError,TypeError):
            return "DENIED"

    def list_users(self,token,after=None,limit=20):
        from auth_admin_directory import list_users
        if not permit(self.path,"admin_global","all",limit=60):
            return None
        try:
            return list_users(self.path,token,after,limit)
        except (ValueError,TypeError):
            return None
