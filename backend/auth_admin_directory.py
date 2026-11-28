"""Owner-only account directory with bounded cursor pagination."""
import re
from auth_db import transaction
from auth_sessions import actor

def list_users(path, token, after=None, limit=50):
    if type(limit) is not int or not 1<=limit<=50:
        raise ValueError("invalid limit")
    if after is not None and (not isinstance(after,str) or not re.fullmatch(r"[0-9a-f]{32}",after)):
        raise ValueError("invalid cursor")
    with transaction(path) as db:
        admin = actor(db,token)
        if admin is None or admin["role"]!="SUPER_ADMIN":
            return None
        rows = db.execute("""SELECT a.id AS accountId,a.username,a.status,
            e.kind AS licenseKind,e.expires AS licenseExpires,
            b.public_key_digest AS deviceDigest
            FROM accounts a LEFT JOIN entitlements e ON e.account_id=a.id
            LEFT JOIN device_bindings b ON b.account_id=a.id
            WHERE a.role='USER' AND a.id>? ORDER BY a.id LIMIT ?""",
            (after or "",limit+1)).fetchall()
        return {"users":[dict(row) for row in rows[:limit]],
                "nextCursor":rows[limit-1]["accountId"] if len(rows)>limit else None}
