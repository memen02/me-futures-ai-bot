"""Service facade for a future authenticated HTTPS adapter; not a listener."""
from auth_accounts import normalize, register
from auth_sessions import login, review, revoke
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
