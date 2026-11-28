import tempfile
import unittest
from pathlib import Path
from auth_db import initialize, transaction
from auth_accounts import register
from auth_sessions import login, review, actor
from auth_security import bootstrap_owner
from auth_entitlements import grant
from auth_devices import bind

class DeviceBindingTest(unittest.TestCase):
    def test_owner_binding_and_replacement(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = str(Path(tmp)/"auth.db")
            initialize(path)
            password = "test-password-123"
            bootstrap_owner(path,"owner",password)
            admin = login(path,"owner",password)["accessToken"]
            targets = []
            for name in ("tester","second"):
                register(path,name,password)
                with transaction(path) as db:
                    target = db.execute("SELECT id FROM accounts WHERE username=?",(name,)).fetchone()[0]
                targets.append(target)
                self.assertEqual(review(path,admin,target,"PENDING","APPROVED"),"APPLIED")
                self.assertEqual(grant(path,admin,target,"FAMILY"),"APPLIED")
            first, second = targets
            token = login(path,"tester",password)["accessToken"]
            self.assertEqual(bind(path,token,first,"a"*64),"DENIED")
            self.assertEqual(bind(path,admin,first,"invalid"),"DENIED")
            self.assertEqual(bind(path,admin,first,"a"*64),"APPLIED")
            with transaction(path) as db:
                self.assertIsNone(actor(db,token))
            self.assertEqual(bind(path,admin,second,"a"*64),"CONFLICT")
            self.assertEqual(bind(path,admin,first,"b"*64),"CONFLICT")
            token = login(path,"tester",password)["accessToken"]
            self.assertEqual(bind(path,admin,first,"b"*64,expected="a"*64),"APPLIED")
            with transaction(path) as db:
                self.assertIsNone(actor(db,token))
                self.assertEqual(db.execute("SELECT public_key_digest FROM device_bindings WHERE account_id=?",(first,)).fetchone()[0],"b"*64)
            self.assertEqual(bind(path,admin,first,"c"*64,expected="a"*64),"CONFLICT")
