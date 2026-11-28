import hashlib
import tempfile
import unittest
from pathlib import Path
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from auth_db import initialize, transaction
from auth_security import bootstrap_owner
from auth_service import AuthService
from auth_sessions import login as legacy_login

class VerifiedServiceTest(unittest.TestCase):
    def test_verified_login(self):
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
            key = ec.generate_private_key(ec.SECP256R1())
            der = key.public_key().public_bytes(serialization.Encoding.DER,serialization.PublicFormat.SubjectPublicKeyInfo)
            digest = hashlib.sha256(der).hexdigest()
            self.assertEqual(service.bind_device(admin,target,digest),"APPLIED")
            self.assertIsNone(service.device_challenge("tester",password,digest))
            self.assertEqual(service.grant_license(admin,target,"FAMILY"),"APPLIED")
            self.assertIsNone(service.login("tester",password))
            self.assertIsNone(legacy_login(path,"tester",password))
            challenge = service.device_challenge("tester",password,digest)
            nonce = challenge["nonce"]
            message = ("ME-PROJECT-DEVICE-LOGIN-V1:"+target+":"+digest+":"+nonce).encode("ascii")
            proof = dict(key_digest=digest,nonce=nonce,public_der=der,signature=key.sign(message,ec.ECDSA(hashes.SHA256())))
            session = service.login("tester",password,proof)
            self.assertIsNotNone(session)
            token = session["accessToken"]
            self.assertIsNotNone(service.authorize(token))
            self.assertEqual(service.bind_device(token,target,"c"*64,expected=digest),"DENIED")
            self.assertEqual(service.grant_license(token,target,"FAMILY"),"DENIED")
            self.assertEqual(service.revoke_license(token,target),"DENIED")
            self.assertIsNone(service.authorize("invalid"))
            service.logout("invalid")
            self.assertIsNone(service.login("tester",password,proof))
            self.assertEqual(service.bind_device(admin,target,"b"*64,expected=digest),"APPLIED")
            self.assertIsNone(service.authorize(token))

    def test_challenge_revocation(self):
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
            self.assertEqual(service.bind_device(admin,target,"a"*64),"APPLIED")
            self.assertEqual(service.grant_license(admin,target,"FAMILY"),"APPLIED")
            self.assertIsNone(service.device_challenge("tester","wrong","a"*64))
            self.assertIsNotNone(service.device_challenge("tester",password,"a"*64))
            self.assertEqual(service.revoke_license(admin,target),"APPLIED")
            with transaction(path) as db:
                self.assertIsNone(db.execute("SELECT nonce FROM device_challenges WHERE account_id=?",(target,)).fetchone())
            self.assertIsNone(service.device_challenge("tester",password,"a"*64))

    def test_owner_session_rotation(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = str(Path(tmp)/"auth.db")
            initialize(path)
            service = AuthService(path)
            password = "test-password-123"
            bootstrap_owner(path,"owner",password)
            first = service.login("owner",password)["accessToken"]
            second = service.login("owner",password)["accessToken"]
            self.assertIsNone(service.authorize(first))
            self.assertEqual(service.authorize(second)["role"],"SUPER_ADMIN")
            self.assertIsNone(service.login("owner","wrong"))
            self.assertIsNotNone(service.authorize(second))
            service.register("tester",password)
            with transaction(path) as db:
                target = db.execute("SELECT id FROM accounts WHERE username='tester'").fetchone()[0]
            self.assertEqual(service.review(second,target,"PENDING","APPROVED"),"APPLIED")
            self.assertEqual(service.grant_license(second,target,"FAMILY"),"APPLIED")
            self.assertIsNone(service.login("tester",password))
            self.assertIsNone(legacy_login(path,"tester",password))
