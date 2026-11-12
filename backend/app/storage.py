from __future__ import annotations

import json
from dataclasses import dataclass, field
from pathlib import Path
from threading import Lock
from typing import Any, Dict

STATE_PATH = Path(__file__).resolve().parent.parent / ".medivox_state.json"
LOCK = Lock()


@dataclass
class AppState:
    users_by_email: Dict[str, dict] = field(default_factory=dict)
    conversations: Dict[str, dict] = field(default_factory=dict)

    def to_dict(self) -> dict[str, Any]:
        return {
            "users_by_email": self.users_by_email,
            "conversations": self.conversations,
        }

    @classmethod
    def from_dict(cls, raw: dict[str, Any]) -> "AppState":
        return cls(
            users_by_email=dict(raw.get("users_by_email", {})),
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


store = LocalStore()
