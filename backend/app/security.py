from __future__ import annotations

import base64
import hashlib
import hmac
import json
import os
import secrets
import time
from dataclasses import dataclass
from typing import Any

from fastapi import HTTPException, status

TOKEN_SECRET = os.getenv("MEDIVOX_TOKEN_SECRET") or os.getenv("MEDIVOX_DEV_TOKEN") or "dev-only-change-me"
TOKEN_TTL_SECONDS = int(os.getenv("MEDIVOX_TOKEN_TTL_SECONDS", "1209600"))
PASSWORD_ITERATIONS = int(os.getenv("MEDIVOX_PASSWORD_ITERATIONS", "210000"))
PASSWORD_ALGORITHM = "sha256"
PASSWORD_SALT_BYTES = 16


@dataclass(frozen=True)
class PasswordRecord:
    salt: str
    hash: str


def _b64url_encode(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).decode("ascii").rstrip("=")


def _b64url_decode(data: str) -> bytes:
    padding = "=" * (-len(data) % 4)
    return base64.urlsafe_b64decode(data + padding)


def hash_password(password: str) -> PasswordRecord:
    salt = secrets.token_bytes(PASSWORD_SALT_BYTES)
    derived = hashlib.pbkdf2_hmac(
        PASSWORD_ALGORITHM,
        password.encode("utf-8"),
        salt,
        PASSWORD_ITERATIONS,
    )
    return PasswordRecord(salt=_b64url_encode(salt), hash=_b64url_encode(derived))


def verify_password(password: str, record: PasswordRecord | dict[str, Any]) -> bool:
    salt = record.salt if isinstance(record, PasswordRecord) else record.get("salt", "")
    expected_hash = record.hash if isinstance(record, PasswordRecord) else record.get("hash", "")
    if not salt or not expected_hash:
        return False
    derived = hashlib.pbkdf2_hmac(
        PASSWORD_ALGORITHM,
        password.encode("utf-8"),
        _b64url_decode(salt),
        PASSWORD_ITERATIONS,
    )
    return hmac.compare_digest(_b64url_encode(derived), expected_hash)


def issue_access_token(user_id: str, email: str) -> str:
    payload = {
        "sub": user_id,
        "email": email,
        "iat": int(time.time()),
        "exp": int(time.time()) + TOKEN_TTL_SECONDS,
        "nonce": secrets.token_hex(8),
    }
    body = _b64url_encode(json.dumps(payload, separators=(",", ":")).encode("utf-8"))
    signature = hmac.new(
        TOKEN_SECRET.encode("utf-8"),
        body.encode("ascii"),
        hashlib.sha256,
    ).digest()
    return f"{body}.{_b64url_encode(signature)}"


def decode_access_token(token: str) -> dict[str, Any]:
    try:
        body, signature = token.split(".", 1)
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token.") from exc

    expected = hmac.new(
        TOKEN_SECRET.encode("utf-8"),
        body.encode("ascii"),
        hashlib.sha256,
    ).digest()
    if not hmac.compare_digest(_b64url_encode(expected), signature):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token.")

    try:
        payload = json.loads(_b64url_decode(body).decode("utf-8"))
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token.") from exc

    if int(payload.get("exp", 0)) < int(time.time()):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Token expired.")
    return payload


def hashed_safety_identifier(user_id: str) -> str:
    return hashlib.sha256(f"{user_id}:{TOKEN_SECRET}".encode("utf-8")).hexdigest()
