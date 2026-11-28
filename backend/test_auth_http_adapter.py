import json
import unittest
from auth_http_adapter import dispatch, MAX_BODY

class StubService:
    def __init__(self):
        self.calls = []
    def login(self,*args):
        self.calls.append(args)
        return None
    def logout(self,token):
        self.calls.append(token)

class HttpAdapterTest(unittest.TestCase):
    def setUp(self):
        self.service = StubService()

    def test_oversized_and_duplicate_json(self):
        for body,status in [(b"x"*(MAX_BODY+1),413),
                            (b'{"username":"tester","username":"other","password":"secret"}',400)]:
            self.assertEqual(dispatch(self.service,"/v1/auth/login",body)[0],status)
        self.assertEqual(self.service.calls,[])

    def test_invalid_route_type(self):
        self.assertEqual(dispatch(self.service,None,b"{}")[0],400)
        self.assertEqual(self.service.calls,[])

    def test_nonstandard_json_constants_rejected(self):
        for value in (b"NaN", b"Infinity", b"-Infinity"):
            body = b'{"username":"tester","password":' + value + b'}'
            self.assertEqual(dispatch(self.service,"/v1/auth/login",body)[0],400)
        self.assertEqual(self.service.calls,[])

    def test_invalid_proof_never_reaches_service(self):
        for proof in (None, {}, {"key_digest":"a"*64,"nonce":"b"*64,
                                  "public_der":"!!!!","signature":"YQ=="}):
            body = json.dumps({"username":"tester","password":"secret","proof":proof}).encode()
            self.assertEqual(dispatch(self.service,"/v1/auth/login",body)[0],400)
        self.assertEqual(self.service.calls,[])

    def test_logout_rejects_extra_fields(self):
        self.assertEqual(dispatch(self.service,"/v1/auth/logout",b'{"extra":true}',"Bearer "+"a"*64)[0],400)
        self.assertEqual(self.service.calls,[])

    def test_logout_requires_valid_bearer(self):
        self.assertEqual(dispatch(self.service,"/v1/auth/logout",b"{}","Bearer bad")[0],400)
        self.assertEqual(self.service.calls,[])
        self.assertEqual(dispatch(self.service,"/v1/auth/logout",b"{}","Bearer "+"a"*64),
                         (200,{"result":"APPLIED"}))
        self.assertEqual(self.service.calls,["a"*64])


class AdminHttpAdapterTest(unittest.TestCase):
    def test_admin_routes_require_bearer(self):
        service = StubService()
        for route in ("review","grant-license","revoke-license","bind-device"):
            for token in (None,"Bearer bad"):
                self.assertEqual(dispatch(service,"/v1/auth/admin/"+route,b"{}",token)[0],401)
        self.assertEqual(service.calls,[])

    def test_admin_rejects_invalid_target_and_fields(self):
        service = StubService()
        token = "Bearer "+"a"*64
        for data in ({"target":"bad"},{"target":"b"*32,"extra":True}):
            self.assertEqual(dispatch(service,"/v1/auth/admin/revoke-license",
                json.dumps(data).encode(),token)[0],400)
        self.assertEqual(service.calls,[])

    def test_user_cannot_mutate_admin_state(self):
        import tempfile
        from pathlib import Path
        from auth_db import initialize, transaction
        from auth_security import bootstrap_owner
        from auth_service import AuthService
        from auth_test_helpers import user_session
        with tempfile.TemporaryDirectory() as tmp:
            path = str(Path(tmp)/"auth.db")
            initialize(path)
            service = AuthService(path)
            password = "test-password-123"
            bootstrap_owner(path,"owner",password)
            admin = service.login("owner",password)["accessToken"]
            service.register("tester",password)
            with transaction(path) as db:
                target = db.execute("SELECT id FROM accounts WHERE username='tester'").fetchone()[0]
            self.assertEqual(service.review(admin,target,"PENDING","APPROVED"),"APPLIED")
            self.assertEqual(service.grant_license(admin,target,"FAMILY"),"APPLIED")
            token = "Bearer "+user_session(path,admin,"tester",password)["accessToken"]
            for route,data in (
                ("review",{"target":target,"expected":"APPROVED","desired":"SUSPENDED"}),
                ("grant-license",{"target":target,"kind":"FAMILY","expires":None}),
                ("revoke-license",{"target":target}),
                ("bind-device",{"target":target,"key_digest":"b"*64,"expected":None})):
                self.assertEqual(dispatch(service,"/v1/auth/admin/"+route,
                    json.dumps(data).encode(),token)[0],403)
            self.assertIsNotNone(service.authorize(token[7:]))
