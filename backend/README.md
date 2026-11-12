# MediVox backend

This backend is a development scaffold for local testing.

## What it provides

- `/health`
- `/auth/register`
- `/auth/login`
- `/auth/me`
- `/api/conversations`
- `/api/conversations/{conversation_id}`
- `/api/conversations/{conversation_id}/messages`
- `/api/realtime/client-secret`

## Local setup

```bash
cd backend
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

Set at least these values in `.env`:

- `MEDIVOX_TOKEN_SECRET`
- `OPENAI_API_KEY`
- `OPENAI_REALTIME_MODEL`

`MEDIVOX_DEV_TOKEN` is optional for legacy test clients.

## Notes

This is a development scaffold, not a production backend.
- Passwords are hashed with PBKDF2 before being stored in the local state file.
- Access tokens are signed and time-limited.
- Auth and conversation state persist locally in `backend/.medivox_state.json`.
- Replace the local file store with a proper database and production identity provider before release.
- Keep the OpenAI API key server-side only.
