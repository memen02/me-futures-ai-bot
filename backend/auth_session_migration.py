"""Offline deployment step; run with authentication service stopped."""
from auth_db import transaction

def invalidate_legacy_sessions(path):
    with transaction(path) as db:
        sessions = db.execute("DELETE FROM sessions").rowcount
        challenges = db.execute("DELETE FROM device_challenges").rowcount
        return {"sessions":sessions,"challenges":challenges}
