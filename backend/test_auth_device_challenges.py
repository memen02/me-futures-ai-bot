import tempfile
import time
import unittest
from pathlib import Path
from auth_db import initialize, transaction
from auth_accounts import register
from auth_sessions import login, review
from auth_security import bootstrap_owner
from auth_devices import bind
from auth_device_challenges import issue, consume

class DeviceChallengeTest(unittest.TestCase):
    def test_challenge_lifecycle(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = str(Path(tmp)/"auth.db")
            initialize(path)
            password = "test-password-123"
            bootstrap_owner(path,"owner",password)
            admin = login(path,"owner",password)["accessToken"]
            register(path,"tester",password)
            with transaction(path) as db:
                target = db.execute("SELECT id FROM accounts WHERE username='tester'").fetchone()[0]
            self.assertEqual(review(path,admin,target,"PENDING","APPROVED"),"APPLIED")
            key = "a"*64
            self.assertIsNone(issue(path,target,key))
            self.assertEqual(bind(path,admin,target,key),"APPLIED")
            old = issue(path,target,key)
            nonce = issue(path,target,key)
            self.assertIsNotNone(nonce)
            self.assertNotEqual(old,nonce)
            with transaction(path) as db:
                now = int(time.time())
                self.assertFalse(consume(db,target,key,old,now))
                self.assertFalse(consume(db,target,"b"*64,nonce,now))
                self.assertTrue(consume(db,target,key,nonce,now))
                self.assertFalse(consume(db,target,key,nonce,now))
            nonce = issue(path,target,key)
            with transaction(path) as db:
                self.assertFalse(consume(db,target,key,nonce,int(time.time())+61))
            nonce = issue(path,target,key)
            self.assertEqual(bind(path,admin,target,"b"*64,expected=key),"APPLIED")
            with transaction(path) as db:
                self.assertFalse(consume(db,target,key,nonce,int(time.time())))
                self.assertEqual(db.execute("SELECT count(*) FROM device_challenges WHERE account_id=?",(target,)).fetchone()[0],0)
