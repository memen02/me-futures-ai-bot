"""Verify P-256 device possession using cryptography; no login integration yet."""
import hashlib
import hmac
import re
from auth_devices import valid_digest

def verify_device_proof(account_id, key_digest, nonce, public_der, signature):
    if not isinstance(account_id,str) or re.fullmatch(r"[0-9a-f]{32}",account_id) is None:
        return False
    if not valid_digest(key_digest) or not valid_digest(nonce):
        return False
    if not isinstance(public_der,bytes) or not 1 <= len(public_der) <= 256:
        return False
    if not isinstance(signature,bytes) or not 1 <= len(signature) <= 80:
        return False
    if not hmac.compare_digest(hashlib.sha256(public_der).hexdigest(),key_digest):
        return False
    try:
        from cryptography.hazmat.primitives import hashes, serialization
        from cryptography.hazmat.primitives.asymmetric import ec
        from cryptography.exceptions import InvalidSignature, UnsupportedAlgorithm
    except ImportError:
        return False
    try:
        key = serialization.load_der_public_key(public_der)
        if not isinstance(key,ec.EllipticCurvePublicKey) or not isinstance(key.curve,ec.SECP256R1):
            return False
        if key.public_bytes(serialization.Encoding.DER,serialization.PublicFormat.SubjectPublicKeyInfo) != public_der:
            return False
        message = ("ME-PROJECT-DEVICE-LOGIN-V1:"+account_id+":"+key_digest+":"+nonce).encode("ascii")
        key.verify(signature,message,ec.ECDSA(hashes.SHA256()))
        return True
    except (ValueError,TypeError,InvalidSignature,UnsupportedAlgorithm):
        return False
