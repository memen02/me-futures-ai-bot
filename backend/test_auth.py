import tempfile
import time
from auth_entitlements import grant, revoke_license
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
        self.assertIsNone(login(self.path,"tester",self.password))
        self.assertEqual(grant(self.path,admin["accessToken"],target,"FAMILY"),"APPLIED")
        user = login(self.path,"tester",self.password)
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
        token = login(self.path,"tester",self.password)["accessToken"]
        self.assertEqual(grant(self.path,token,target,"FAMILY"),"DENIED")
        with transaction(self.path) as db:
            self.assertIsNotNone(actor(db,token))
            db.execute("UPDATE entitlements SET expires=? WHERE account_id=?",(int(time.time())-1,target))
            self.assertIsNone(actor(db,token))
        self.assertIsNone(login(self.path,"tester",self.password))
        self.assertEqual(grant(self.path,admin,target,"FAMILY"),"APPLIED")
        token = login(self.path,"tester",self.password)["accessToken"]
        self.assertEqual(revoke_license(self.path,admin,target),"APPLIED")
        with transaction(self.path) as db:
            self.assertIsNone(actor(db,token))
        self.assertIsNone(login(self.path,"tester",self.password))

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
        token = login(self.path,"tester",self.password)["accessToken"]
        self.assertEqual(grant(self.path,admin,target,"MONTHLY",now+1800),"CONFLICT")
        self.assertEqual(grant(self.path,admin,target,"MONTHLY",now+7200),"APPLIED")
        with transaction(self.path) as db:
            self.assertIsNone(actor(db,token))
            self.assertEqual(db.execute("SELECT expires FROM entitlements WHERE account_id=?",(target,)).fetchone()[0],now+7200)
        self.assertIsNotNone(login(self.path,"tester",self.password))
