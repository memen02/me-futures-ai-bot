import hashlib
import unittest
from auth_device_proof import verify_device_proof

class DeviceProofTest(unittest.TestCase):
    def test_invalid_input(self):
        self.assertFalse(verify_device_proof("a"*32,"b"*64,"c"*64,b"invalid",b"invalid"))

    def test_signature(self):
        from cryptography.hazmat.primitives import hashes, serialization
        from cryptography.hazmat.primitives.asymmetric import ec
        key = ec.generate_private_key(ec.SECP256R1())
        der = key.public_key().public_bytes(serialization.Encoding.DER,serialization.PublicFormat.SubjectPublicKeyInfo)
        digest = hashlib.sha256(der).hexdigest()
        account, nonce = "a"*32, "b"*64
        message = ("ME-PROJECT-DEVICE-LOGIN-V1:"+account+":"+digest+":"+nonce).encode("ascii")
        signature = key.sign(message,ec.ECDSA(hashes.SHA256()))
        self.assertTrue(verify_device_proof(account,digest,nonce,der,signature))
        self.assertFalse(verify_device_proof(account,digest,"c"*64,der,signature))
        self.assertFalse(verify_device_proof("d"*32,digest,nonce,der,signature))
        self.assertFalse(verify_device_proof(account,"e"*64,nonce,der,signature))
        self.assertFalse(verify_device_proof(account,digest,nonce,der,b"invalid"))
