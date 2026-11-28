import tempfile
import unittest
from pathlib import Path
from auth_db import initialize
from auth_security import bootstrap_owner
from auth_accounts import register
from auth_sessions import owner_login
from auth_admin_directory import list_users

class AdminDirectoryTest(unittest.TestCase):
    def test_directory(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = str(Path(tmp)/"auth.db")
            initialize(path)
            password = "test-password-123"
            bootstrap_owner(path,"owner",password)
            token = owner_login(path,"owner",password)["accessToken"]
            for name in ("tester","second","third"):
                register(path,name,password)
            self.assertIsNone(list_users(path,"a"*64))
            first = list_users(path,token,limit=2)
            self.assertEqual(len(first["users"]),2)
            self.assertIsNotNone(first["nextCursor"])
            second = list_users(path,token,after=first["nextCursor"],limit=2)
            self.assertEqual(len(second["users"]),1)
            self.assertIsNone(second["nextCursor"])
            users = first["users"] + second["users"]
            self.assertEqual({u["username"] for u in users},{"tester","second","third"})
            for user in users:
                self.assertEqual(set(user),{"accountId","username","status","licenseKind","licenseExpires","deviceDigest"})
            for limit in (0,51,True):
                with self.assertRaises(ValueError):
                    list_users(path,token,limit=limit)
            with self.assertRaises(ValueError):
                list_users(path,token,after="invalid")
