from __future__ import annotations

from datetime import datetime, timezone
from typing import Optional
from uuid import uuid4

import httpx
from fastapi import Depends, FastAPI, Header, HTTPException, status
from pydantic import BaseModel, EmailStr, Field

from .security import (
    decode_access_token,
    hashed_safety_identifier,
    hash_password,
    issue_access_token,
    verify_password,
)
from .storage import store

app = FastAPI(title="MediVox API", version="0.3.0")

OPENAI_API_KEY = ""
OPENAI_REALTIME_MODEL = "gpt-realtime-2.1"


class AuthRequest(BaseModel):
    email: EmailStr
    password: str = Field(min_length=1)
    display_name: Optional[str] = None


class UserResponse(BaseModel):
    id: str
    email: EmailStr
    display_name: str


class AuthResponse(BaseModel):
    access_token: str
    user: UserResponse


class ConversationCreateRequest(BaseModel):
    title: str = Field(min_length=1)


class ConversationMessageRequest(BaseModel):
    role: str = Field(pattern=r"^(user|assistant|system)$")
    content: str = Field(min_length=1)


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def normalize_display_name(display_name: Optional[str], email: str) -> str:
    cleaned = (display_name or "").strip()
    if cleaned:
        return cleaned
    fallback = email.split("@", 1)[0].strip() or "MediVox User"
    return fallback.replace(".", " ").replace("_", " ").title()


def get_user_id(authorization: str | None = Header(default=None)) -> str:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Missing bearer token.")
    token = authorization.removeprefix("Bearer ").strip()
    payload = decode_access_token(token)
    return str(payload["sub"])


def current_user(user_id: str) -> dict:
    for user in store.state.users_by_email.values():
        if user.get("id") == user_id:
            return user
    raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid session.")


def to_user_response(user: dict) -> UserResponse:
    return UserResponse(id=user["id"], email=user["email"], display_name=user["display_name"])


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "service": "medivox-api"}


@app.post("/auth/register", response_model=AuthResponse)
def register(payload: AuthRequest) -> AuthResponse:
    email = payload.email.lower().strip()
    if email in store.state.users_by_email:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Email already registered.")

    password_record = hash_password(payload.password)
    user = {
        "id": str(uuid4()),
        "email": email,
        "display_name": normalize_display_name(payload.display_name, email),
        "password": password_record.__dict__,
    }
    store.state.users_by_email[email] = user
    store.save()
    token = issue_access_token(user["id"], user["email"])
    return AuthResponse(access_token=token, user=to_user_response(user))


@app.post("/auth/login", response_model=AuthResponse)
def login(payload: AuthRequest) -> AuthResponse:
    email = payload.email.lower().strip()
    user = store.state.users_by_email.get(email)
    if not user or not verify_password(payload.password, user.get("password", {})):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid email or password.")
    token = issue_access_token(user["id"], user["email"])
    return AuthResponse(access_token=token, user=to_user_response(user))


@app.get("/auth/me")
def me(user_id: str = Depends(get_user_id)) -> dict:
    user = current_user(user_id)
    return {"user": to_user_response(user).model_dump()}


@app.post("/api/realtime/client-secret")
async def create_client_secret(user_id: str = Depends(get_user_id)) -> dict[str, str]:
    if not OPENAI_API_KEY:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="OPENAI_API_KEY is not configured on the backend.",
        )

    hashed_user = hashed_safety_identifier(user_id)
    session_config = {
        "type": "realtime",
        "model": OPENAI_REALTIME_MODEL,
        "instructions": (
            "You are MediVox, a calm conversational health information assistant. "
            "Use concise, natural speech. Explain uncertainty. Do not claim to "
            "diagnose or replace professional care. Urgent symptoms should be "
            "escalated to local emergency services or in-person care."
        ),
        "output_modalities": ["audio"],
        "audio": {
            "input": {
                "turn_detection": {
                    "type": "server_vad",
                    "threshold": 0.5,
                    "prefix_padding_ms": 300,
                    "silence_duration_ms": 550,
                }
            },
            "output": {"voice": "marin"},
        },
    }

    async with httpx.AsyncClient(timeout=20.0) as client:
        response = await client.post(
            "https://api.openai.com/v1/realtime/client_secrets",
            headers={
                "Authorization": f"Bearer {OPENAI_API_KEY}",
                "Content-Type": "application/json",
                "OpenAI-Safety-Identifier": hashed_user,
            },
            json={
                "session": session_config,
            },
        )

    if response.is_error:
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail="OpenAI could not create a Realtime client secret.",
        )

    data = response.json()
    value = data.get("value") or data.get("client_secret", {}).get("value")
    if not value:
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail="OpenAI returned an unexpected client secret response.",
        )
    return {"value": value, "expires_at": data.get("expires_at", now_iso())}


@app.post("/api/conversations")
def create_conversation(payload: ConversationCreateRequest, user_id: str = Depends(get_user_id)) -> dict:
    conversation_id = str(uuid4())
    conversation = {
        "id": conversation_id,
        "user_id": user_id,
        "title": payload.title,
        "updated_at": now_iso(),
        "messages": [],
    }
    store.state.conversations[conversation_id] = conversation
    store.save()
    return {"conversation": {"id": conversation_id, "title": payload.title, "updated_at": conversation["updated_at"]}}


@app.get("/api/conversations")
def list_conversations(user_id: str = Depends(get_user_id)) -> dict:
    conversations = [
        {
            "id": c["id"],
            "title": c["title"],
            "updated_at": c["updated_at"],
        }
        for c in store.state.conversations.values()
        if c["user_id"] == user_id
    ]
    conversations.sort(key=lambda item: item["updated_at"], reverse=True)
    return {"conversations": conversations}


@app.get("/api/conversations/{conversation_id}")
def get_conversation(conversation_id: str, user_id: str = Depends(get_user_id)) -> dict:
    conversation = store.state.conversations.get(conversation_id)
    if not conversation or conversation["user_id"] != user_id:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Conversation not found.")
    return {"conversation": conversation}


@app.post("/api/conversations/{conversation_id}/messages")
def add_message(conversation_id: str, payload: ConversationMessageRequest, user_id: str = Depends(get_user_id)) -> dict:
    conversation = store.state.conversations.get(conversation_id)
    if not conversation or conversation["user_id"] != user_id:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Conversation not found.")
    message = {
        "role": payload.role,
        "content": payload.content,
        "created_at": now_iso(),
    }
    conversation["messages"].append(message)
    conversation["updated_at"] = message["created_at"]
    store.save()
    return {"message": message}


@app.delete("/api/conversations/{conversation_id}")
def delete_conversation(conversation_id: str, user_id: str = Depends(get_user_id)) -> dict:
    conversation = store.state.conversations.get(conversation_id)
    if not conversation or conversation["user_id"] != user_id:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Conversation not found.")
    del store.state.conversations[conversation_id]
    store.save()
    return {"deleted": True, "conversation_id": conversation_id}
