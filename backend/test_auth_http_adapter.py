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

    def test_invalid_proof_never_reaches_service(self):
        for proof in (None, {}, {"key_digest":"a"*64,"nonce":"b"*64,
                                  "public_der":"!!!!","signature":"YQ=="}):
            body = json.dumps({"username":"tester","password":"secret","proof":proof}).encode()
            self.assertEqual(dispatch(self.service,"/v1/auth/login",body)[0],400)
        self.assertEqual(self.service.calls,[])

    def test_logout_requires_valid_bearer(self):
        self.assertEqual(dispatch(self.service,"/v1/auth/logout",b"{}","Bearer bad")[0],400)
        self.assertEqual(self.service.calls,[])
        self.assertEqual(dispatch(self.service,"/v1/auth/logout",b"{}","Bearer "+"a"*64),
                         (200,{"result":"APPLIED"}))
        self.assertEqual(self.service.calls,["a"*64])
