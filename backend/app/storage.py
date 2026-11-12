from __future__ import annotations

import json
from dataclasses import dataclass, field
from pathlib import Path
from threading import Lock
from typing import Any, Dict, List
from uuid import uuid4

STATE_PATH = Path(__file__).resolve().parent.parent / ".medivox_state.json"
LOCK = Lock()


@dataclass
class AppState:
    users_by_email: Dict[str, dict] = field(default_factory=dict)
    tokens: Dict[str, str] = field(default_factory=dict)
    conversations: Dict[str, dict] = field(default_factory=dict)

    def to_dict(self) -> dict[str, Any]:
        return {
            "users_by_email": self.users_by_email,
            "tokens": self.tokens,
            "conversations": self.conversations,
        }

    @classmethod
    def from_dict(cls, raw: dict[str, Any]) -> "AppState":
        return cls(
            users_by_email=dict(raw.get("users_by_email", {})),
            tokens=dict(raw.get("tokens", {})),
            conversations=dict(raw.get("conversations", {})),
        )


class LocalStore:
    def __init__(self, path: Path = STATE_PATH) -> None:
        self.path = path
        self.state = self._load()

    def _load(self) -> AppState:
        if not self.path.exists():
            return AppState()
        try:
            return AppState.from_dict(json.loads(self.path.read_text(encoding="utf-8")))
        except Exception:
            return AppState()

    def save(self) -> None:
        with LOCK:
            self.path.write_text(
                json.dumps(self.state.to_dict(), indent=2, ensure_ascii=False),
                encoding="utf-8",
            )

    def new_token(self, user_id: str) -> str:
        token = str(uuid4())
        self.state.tokens[token] = user_id
        self.save()
        return token

    def ensure_conversation(self, conversation_id: str, user_id: str, title: str) -> dict:
        conversation = {
            "id": conversation_id,
            "user_id": user_id,
            "title": title,
            "updated_at": None,
            "messages": [],
        }
        self.state.conversations[conversation_id] = conversation
        self.save()
        return conversation


store = LocalStore()
