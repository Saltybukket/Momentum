import hashlib
import hmac
import secrets


def create_opaque_token() -> str:
    return secrets.token_urlsafe(32)


def hash_token(token: str, pepper: str) -> str:
    return hmac.new(pepper.encode(), token.encode(), hashlib.sha256).hexdigest()


def request_fingerprint(body: bytes) -> str:
    return hashlib.sha256(body).hexdigest()
