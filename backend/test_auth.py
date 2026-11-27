import tempfile
import unittest
from pathlib import Path
from auth_db import initialize, transaction
from auth_accounts import register, hash_password, verify_password
from auth_sessions import login, review, actor, revoke
from auth_security import bootstrap_owner, permit

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
        user = login(self.path,"tester",self.password)
        self.assertEqual(review(self.path,user["accessToken"],target,"APPROVED","SUSPENDED"),"DENIED")
        self.assertEqual(review(self.path,admin["accessToken"],target,"APPROVED","SUSPENDED"),"APPLIED")
        with transaction(self.path) as db:
            self.assertIsNone(actor(db,user["accessToken"]))
        revoke(self.path,admin["accessToken"])
        with transaction(self.path) as db:
            self.assertIsNone(actor(db,admin["accessToken"]))

    def test_rate_limit(self):
        for _ in range(5):
            self.assertTrue(permit(self.path,"login_user","tester"))
        self.assertFalse(permit(self.path,"login_user","tester"))
        self.assertTrue(permit(self.path,"login_user","other"))
