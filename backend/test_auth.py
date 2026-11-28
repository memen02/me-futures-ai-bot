import tempfile
import time
from auth_entitlements import grant, revoke_license
import unittest
from pathlib import Path
from auth_db import initialize, transaction
from auth_accounts import register, hash_password, verify_password
from auth_sessions import login, review, actor, revoke
from auth_security import bootstrap_owner, permit
from auth_test_helpers import user_session

class AuthTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.path = str(Path(self.tmp.name)/"auth.db")
        initialize(self.path)
        self.password = "test-password-123"

    def test_password(self):
        a = hash_password(self.password)
        self.assertNotEqual(a,hash_password(self.password))
        self.assertTrue(verify_password(self.password,a))
        self.assertFalse(verify_password("wrong",a))

    def test_malformed_password_hash_fails_closed(self):
        for value in (None, 123, "", "a"*32+":"+"b"*128+" ", "a"*10000):
            self.assertFalse(verify_password(self.password,value))

    def test_approval_and_revocation(self):
        bootstrap_owner(self.path,"owner",self.password)
        with self.assertRaises(ValueError):
            bootstrap_owner(self.path,"other",self.password)
        self.assertTrue(register(self.path,"Tester",self.password))
        self.assertFalse(register(self.path,"tester",self.password))
        self.assertIsNone(login(self.path,"tester",self.password))
        admin = login(self.path,"owner",self.password)
        with transaction(self.path) as db:
            target = db.execute("SELECT id FROM accounts WHERE username='tester'").fetchone()["id"]
        self.assertEqual(review(self.path,admin["accessToken"],target,"PENDING","APPROVED"),"APPLIED")
        self.assertIsNone(login(self.path,"tester",self.password))
        self.assertEqual(grant(self.path,admin["accessToken"],target,"FAMILY"),"APPLIED")
        user = user_session(self.path,admin["accessToken"],"tester",self.password)
        self.assertIsNotNone(user)
        self.assertEqual(review(self.path,user["accessToken"],target,"APPROVED","SUSPENDED"),"DENIED")
        self.assertEqual(review(self.path,admin["accessToken"],target,"APPROVED","SUSPENDED"),"APPLIED")
        with transaction(self.path) as db:
            self.assertIsNone(actor(db,user["accessToken"]))
        revoke(self.path,admin["accessToken"])
        with transaction(self.path) as db:
            self.assertIsNone(actor(db,admin["accessToken"]))

    def test_license_expiry_and_revocation(self):
        bootstrap_owner(self.path,"owner",self.password)
        register(self.path,"tester",self.password)
        admin = login(self.path,"owner",self.password)["accessToken"]
        with transaction(self.path) as db:
            target = db.execute("SELECT id FROM accounts WHERE username='tester'").fetchone()["id"]
        self.assertEqual(review(self.path,admin,target,"PENDING","APPROVED"),"APPLIED")
        self.assertIsNone(login(self.path,"tester",self.password))
        self.assertEqual(grant(self.path,admin,target,"MONTHLY",int(time.time())-1),"DENIED")
        self.assertEqual(grant(self.path,admin,target,"MONTHLY",int(time.time())+3600),"APPLIED")
        token = user_session(self.path,admin,"tester",self.password)["accessToken"]
        self.assertEqual(grant(self.path,token,target,"FAMILY"),"DENIED")
        with transaction(self.path) as db:
            self.assertIsNotNone(actor(db,token))
            db.execute("UPDATE entitlements SET expires=? WHERE account_id=?",(int(time.time())-1,target))
            self.assertIsNone(actor(db,token))
        self.assertIsNone(login(self.path,"tester",self.password))
        self.assertEqual(grant(self.path,admin,target,"FAMILY"),"APPLIED")
        token = user_session(self.path,admin,"tester",self.password)["accessToken"]
        self.assertEqual(revoke_license(self.path,admin,target),"APPLIED")
        with transaction(self.path) as db:
            self.assertIsNone(actor(db,token))
        self.assertIsNone(login(self.path,"tester",self.password))

    def test_owner_relogin_invalidates_old_token_and_old_logout_preserves_new(self):
        bootstrap_owner(self.path,"owner",self.password)
        old = login(self.path,"owner",self.password)["accessToken"]
        new = login(self.path,"owner",self.password)["accessToken"]
        self.assertNotEqual(old,new)
        revoke(self.path,old)
        with transaction(self.path) as db:
            self.assertIsNone(actor(db,old))
            self.assertIsNotNone(actor(db,new))
        revoke(self.path,new)
        revoke(self.path,new)
        with transaction(self.path) as db:
            self.assertIsNone(actor(db,new))

    def test_owner_expiry_boundary(self):
        from unittest.mock import patch
        bootstrap_owner(self.path,"owner",self.password)
        with patch("auth_sessions.time.time", return_value=1000):
            token = login(self.path,"owner",self.password)["accessToken"]
        with transaction(self.path) as db:
            with patch("auth_sessions.time.time", return_value=1899):
                self.assertIsNotNone(actor(db,token))
            with patch("auth_sessions.time.time", return_value=1900):
                self.assertIsNone(actor(db,token))

    def test_failed_owner_login_preserves_session(self):
        bootstrap_owner(self.path,"owner",self.password)
        token = login(self.path,"owner",self.password)["accessToken"]
        self.assertIsNone(login(self.path,"owner","wrong-password"))
        with transaction(self.path) as db:
            self.assertIsNotNone(actor(db,token))

    def test_expired_owner_cannot_approve(self):
        bootstrap_owner(self.path,"owner",self.password)
        register(self.path,"tester",self.password)
        token = login(self.path,"owner",self.password)["accessToken"]
        with transaction(self.path) as db:
            target = db.execute("SELECT id FROM accounts WHERE username='tester'").fetchone()[0]
            db.execute("UPDATE sessions SET expires=0")
        self.assertEqual(review(self.path,token,target,"PENDING","APPROVED"),"DENIED")
        with transaction(self.path) as db:
            self.assertEqual(db.execute("SELECT status FROM accounts WHERE id=?",(target,)).fetchone()[0],"PENDING")

    def test_service_recreation_respects_revocation(self):
        from auth_service import AuthService
        bootstrap_owner(self.path,"owner",self.password)
        token = login(self.path,"owner",self.password)["accessToken"]
        self.assertIsNotNone(AuthService(self.path).authorize(token))
        AuthService(self.path).logout(token)
        self.assertIsNone(AuthService(self.path).authorize(token))

    def test_license_rejects_boolean_expiry(self):
        bootstrap_owner(self.path,"owner",self.password)
        register(self.path,"tester",self.password)
        admin = login(self.path,"owner",self.password)["accessToken"]
        with transaction(self.path) as db:
            target = db.execute("SELECT id FROM accounts WHERE username='tester'").fetchone()[0]
        self.assertEqual(review(self.path,admin,target,"PENDING","APPROVED"),"APPLIED")
        self.assertEqual(grant(self.path,admin,target,"MONTHLY",True),"DENIED")
        self.assertEqual(grant(self.path,admin,target,"FAMILY",0),"DENIED")
        with transaction(self.path) as db:
            self.assertIsNone(db.execute("SELECT 1 FROM entitlements WHERE account_id=?",(target,)).fetchone())

    def test_owner_cannot_receive_user_license(self):
        bootstrap_owner(self.path,"owner",self.password)
        admin = login(self.path,"owner",self.password)
        self.assertEqual(grant(self.path,admin["accessToken"],admin["accountId"],"FAMILY"),"DENIED")

    def test_enrolled_owner_requires_device_proof(self):
        import hashlib
        from cryptography.hazmat.primitives import hashes, serialization
        from cryptography.hazmat.primitives.asymmetric import ec
        from auth_device_challenges import issue_authenticated
        from auth_verified_login import verified_login
        bootstrap_owner(self.path,"owner",self.password)
        key = ec.generate_private_key(ec.SECP256R1())
        der = key.public_key().public_bytes(serialization.Encoding.DER,serialization.PublicFormat.SubjectPublicKeyInfo)
        digest = hashlib.sha256(der).hexdigest()
        with transaction(self.path) as db:
            account = db.execute("SELECT id FROM accounts WHERE role='SUPER_ADMIN'").fetchone()[0]
            db.execute("INSERT INTO device_bindings VALUES(?,?)",(account,digest))
        self.assertIsNone(login(self.path,"owner",self.password))
        challenge = issue_authenticated(self.path,"owner",self.password,digest)
        self.assertIsNotNone(challenge)
        nonce = challenge["nonce"]
        message = ("ME-PROJECT-DEVICE-LOGIN-V1:"+account+":"+digest+":"+nonce).encode("ascii")
        signature = key.sign(message,ec.ECDSA(hashes.SHA256()))
        result = verified_login(self.path,"owner",self.password,digest,nonce,der,signature)
        self.assertEqual(result["role"],"SUPER_ADMIN")
        self.assertIsNone(verified_login(self.path,"owner",self.password,digest,nonce,der,signature))

    def recovery_proof(self):
        import hashlib
        from cryptography.hazmat.primitives import hashes, serialization
        from cryptography.hazmat.primitives.asymmetric import ec
        from auth_owner_recovery import issue_recovery_challenge
        key = ec.generate_private_key(ec.SECP256R1())
        der = key.public_key().public_bytes(serialization.Encoding.DER,serialization.PublicFormat.SubjectPublicKeyInfo)
        digest = hashlib.sha256(der).hexdigest()
        challenge = issue_recovery_challenge(self.path,"owner",digest)
        nonce = challenge["nonce"]
        message = ("ME-PROJECT-DEVICE-LOGIN-V1:"+challenge["accountId"]+":"+digest+":"+nonce).encode("ascii")
        return digest, {"nonce":nonce,"public_der":der,"signature":key.sign(message,ec.ECDSA(hashes.SHA256()))}

    def test_offline_owner_recovery_revokes_old_session_and_consumes_secret(self):
        from auth_owner_recovery import provision, recover
        bootstrap_owner(self.path,"owner",self.password)
        old = login(self.path,"owner",self.password)["accessToken"]
        secret = provision(self.path,"owner")
        self.assertFalse(recover(self.path,"owner","wrong",secret,"a"*64))
        digest, proof = self.recovery_proof()
        self.assertTrue(recover(self.path,"owner",self.password,secret,digest,**proof))
        self.assertFalse(recover(self.path,"owner",self.password,secret,"b"*64))
        with transaction(self.path) as db:
            self.assertIsNone(actor(db,old))
            self.assertEqual(db.execute("SELECT public_key_digest FROM device_bindings").fetchone()[0],digest)
            self.assertIsNone(db.execute("SELECT 1 FROM owner_recovery").fetchone())
        self.assertIsNone(login(self.path,"owner",self.password))

    def test_owner_recovery_rate_limit_persists(self):
        from auth_owner_recovery import provision, recover
        bootstrap_owner(self.path,"owner",self.password)
        secret = provision(self.path,"owner")
        for _ in range(5):
            self.assertFalse(recover(self.path,"owner","wrong",secret,"a"*64))
        self.assertFalse(recover(self.path,"owner",self.password,secret,"a"*64))
        with transaction(self.path) as db:
            self.assertIsNotNone(db.execute("SELECT 1 FROM owner_recovery").fetchone())
            self.assertIsNone(db.execute("SELECT 1 FROM device_bindings").fetchone())

    def test_recovery_provision_rotates_secret(self):
        from auth_owner_recovery import provision, recover
        bootstrap_owner(self.path,"owner",self.password)
        old = provision(self.path,"owner")
        new = provision(self.path,"owner")
        self.assertNotEqual(old,new)
        self.assertFalse(recover(self.path,"owner",self.password,old,"a"*64))
        digest, proof = self.recovery_proof()
        self.assertTrue(recover(self.path,"owner",self.password,new,digest,**proof))

    def test_owner_recovery_challenge_requires_matching_proof(self):
        import hashlib
        from cryptography.hazmat.primitives import hashes, serialization
        from cryptography.hazmat.primitives.asymmetric import ec
        from auth_owner_recovery import provision, recover, issue_recovery_challenge
        bootstrap_owner(self.path,"owner",self.password)
        secret = provision(self.path,"owner")
        key = ec.generate_private_key(ec.SECP256R1())
        der = key.public_key().public_bytes(serialization.Encoding.DER,serialization.PublicFormat.SubjectPublicKeyInfo)
        digest = hashlib.sha256(der).hexdigest()
        challenge = issue_recovery_challenge(self.path,"owner",digest)
        nonce = challenge["nonce"]
        message = ("ME-PROJECT-DEVICE-LOGIN-V1:"+challenge["accountId"]+":"+digest+":"+nonce).encode("ascii")
        signature = key.sign(message,ec.ECDSA(hashes.SHA256()))
        self.assertFalse(recover(self.path,"owner",self.password,secret,digest))
        self.assertFalse(recover(self.path,"owner",self.password,secret,digest,nonce=nonce,public_der=der,signature=b"bad"))
        self.assertTrue(recover(self.path,"owner",self.password,secret,digest,nonce=nonce,public_der=der,signature=signature))
        self.assertFalse(recover(self.path,"owner",self.password,secret,digest,nonce=nonce,public_der=der,signature=signature))

    def test_owner_recovery_without_issued_challenge_denied(self):
        from auth_owner_recovery import provision, recover
        bootstrap_owner(self.path,"owner",self.password)
        secret = provision(self.path,"owner")
        self.assertFalse(recover(self.path,"owner",self.password,secret,"a"*64))
        with transaction(self.path) as db:
            self.assertIsNotNone(db.execute("SELECT 1 FROM owner_recovery").fetchone())
            self.assertIsNone(db.execute("SELECT 1 FROM device_bindings").fetchone())

    def test_rate_limit(self):
        for _ in range(5):
            self.assertTrue(permit(self.path,"login_user","tester"))
        self.assertFalse(permit(self.path,"login_user","tester"))
        self.assertTrue(permit(self.path,"login_user","other"))

    def test_monthly_renewal(self):
        bootstrap_owner(self.path,"owner",self.password)
        register(self.path,"tester",self.password)
        admin = login(self.path,"owner",self.password)["accessToken"]
        with transaction(self.path) as db:
            target = db.execute("SELECT id FROM accounts WHERE username='tester'").fetchone()["id"]
        self.assertEqual(review(self.path,admin,target,"PENDING","APPROVED"),"APPLIED")
        now = int(time.time())
        self.assertEqual(grant(self.path,admin,target,"MONTHLY",now+32*86400),"DENIED")
        self.assertEqual(grant(self.path,admin,target,"MONTHLY",now+3600),"APPLIED")
        token = user_session(self.path,admin,"tester",self.password)["accessToken"]
        self.assertEqual(grant(self.path,admin,target,"MONTHLY",now+1800),"CONFLICT")
        self.assertEqual(grant(self.path,admin,target,"MONTHLY",now+7200),"APPLIED")
        with transaction(self.path) as db:
            self.assertIsNone(actor(db,token))
            self.assertEqual(db.execute("SELECT expires FROM entitlements WHERE account_id=?",(target,)).fetchone()[0],now+7200)
        self.assertIsNotNone(user_session(self.path,admin,"tester",self.password))
