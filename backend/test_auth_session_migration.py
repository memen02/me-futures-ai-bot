import tempfile
import unittest
from pathlib import Path
from auth_db import initialize, transaction
from auth_security import bootstrap_owner
from auth_sessions import owner_login, actor
from auth_session_migration import invalidate_legacy_sessions

class SessionMigrationTest(unittest.TestCase):
    def test_invalidation(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = str(Path(tmp)/"auth.db")
            initialize(path)
            password = "test-password-123"
            bootstrap_owner(path,"owner",password)
            token = owner_login(path,"owner",password)["accessToken"]
            self.assertEqual(invalidate_legacy_sessions(path),{"sessions":1,"challenges":0})
            with transaction(path) as db:
                self.assertIsNone(actor(db,token))
                self.assertEqual(db.execute("SELECT count(*) FROM accounts").fetchone()[0],1)
            self.assertEqual(invalidate_legacy_sessions(path),{"sessions":0,"challenges":0})
            self.assertIsNotNone(owner_login(path,"owner",password))
