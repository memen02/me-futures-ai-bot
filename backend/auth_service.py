"""Service facade for a future authenticated HTTPS adapter; not a listener."""
from auth_accounts import normalize, register
from auth_sessions import login, review, revoke, actor
from auth_db import transaction
from auth_entitlements import grant, revoke_license
from auth_security import permit

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

    def login(self,username,password):
        if not permit(self.path,"login_global","all",limit=60):
            return None
        try:
            username=normalize(username)
            if not permit(self.path,"login_user",username):
                return None
            return login(self.path,username,password)
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
        revoke(self.path,token)

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
