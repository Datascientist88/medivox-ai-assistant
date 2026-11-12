from __future__ import annotations

from datetime import datetime, timezone
from typing import Dict, List, Optional
from uuid import uuid4

from fastapi import Depends, FastAPI, Header, HTTPException, status
from pydantic import BaseModel, EmailStr, Field

app = FastAPI(title="MediVox API", version="0.1.0")

MEDIVOX_DEV_TOKEN = ""

# In-memory development stores.
USERS_BY_EMAIL: Dict[str, dict] = {}
TOKENS: Dict[str, str] = {}
CONVERSATIONS: Dict[str, dict] = {}


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
    role: str
    content: str = Field(min_length=1)


async def require_dev_token(x_medivox_dev_token: str | None = Header(default=None)) -> None:
    if not MEDIVOX_DEV_TOKEN:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="MEDIVOX_DEV_TOKEN is not configured on the backend.",
        )
    if x_medivox_dev_token != MEDIVOX_DEV_TOKEN:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Unauthorized.")


def token_to_user_id(authorization: str | None = Header(default=None)) -> str:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Missing bearer token.")
    token = authorization.removeprefix("Bearer ").strip()
    user_id = TOKENS.get(token)
    if not user_id:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token.")
    return user_id


def to_user_response(user: dict) -> UserResponse:
    return UserResponse(id=user["id"], email=user["email"], display_name=user["display_name"])


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "service": "medivox-api"}


@app.post("/auth/register", response_model=AuthResponse)
def register(payload: AuthRequest) -> AuthResponse:
    email = payload.email.lower().strip()
    if email in USERS_BY_EMAIL:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Email already registered.")

    user = {
        "id": str(uuid4()),
        "email": email,
        "display_name": (payload.display_name or email.split("@", 1)[0]).strip(),
        "password": payload.password,
    }
    USERS_BY_EMAIL[email] = user
    token = str(uuid4())
    TOKENS[token] = user["id"]
    return AuthResponse(access_token=token, user=to_user_response(user))


@app.post("/auth/login", response_model=AuthResponse)
def login(payload: AuthRequest) -> AuthResponse:
    email = payload.email.lower().strip()
    user = USERS_BY_EMAIL.get(email)
    if not user or user["password"] != payload.password:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid email or password.")
    token = str(uuid4())
    TOKENS[token] = user["id"]
    return AuthResponse(access_token=token, user=to_user_response(user))


@app.get("/auth/me")
def me(user_id: str = Depends(token_to_user_id)) -> dict:
    user = next((u for u in USERS_BY_EMAIL.values() if u["id"] == user_id), None)
    if not user:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid session.")
    return {"user": to_user_response(user).model_dump()}


@app.post("/api/realtime/client-secret")
def create_client_secret(_: None = Depends(require_dev_token)) -> dict[str, str]:
    # Development placeholder: in a real deployment, mint a short-lived OpenAI Realtime secret here.
    return {
        "value": "demo-realtime-client-secret",
        "expires_at": datetime.now(timezone.utc).isoformat(),
    }


@app.post("/api/conversations")
def create_conversation(payload: ConversationCreateRequest, user_id: str = Depends(token_to_user_id)) -> dict:
    conversation_id = str(uuid4())
    conversation = {
        "id": conversation_id,
        "user_id": user_id,
        "title": payload.title,
        "updated_at": datetime.now(timezone.utc).isoformat(),
        "messages": [],
    }
    CONVERSATIONS[conversation_id] = conversation
    return {"conversation": {"id": conversation_id, "title": payload.title, "updated_at": conversation["updated_at"]}}


@app.get("/api/conversations")
def list_conversations(user_id: str = Depends(token_to_user_id)) -> dict:
    conversations = [
        {
            "id": c["id"],
            "title": c["title"],
            "updated_at": c["updated_at"],
        }
        for c in CONVERSATIONS.values()
        if c["user_id"] == user_id
    ]
    conversations.sort(key=lambda item: item["updated_at"], reverse=True)
    return {"conversations": conversations}


@app.post("/api/conversations/{conversation_id}/messages")
def add_message(conversation_id: str, payload: ConversationMessageRequest, user_id: str = Depends(token_to_user_id)) -> dict:
    conversation = CONVERSATIONS.get(conversation_id)
    if not conversation or conversation["user_id"] != user_id:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Conversation not found.")
    message = {
        "role": payload.role,
        "content": payload.content,
        "created_at": datetime.now(timezone.utc).isoformat(),
    }
    conversation["messages"].append(message)
    conversation["updated_at"] = message["created_at"]
    return {"message": message}
