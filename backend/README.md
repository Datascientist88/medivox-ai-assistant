# MediVox backend

This backend is a development scaffold for local testing.

## What it provides

- `/health`
- `/auth/register`
- `/auth/login`
- `/auth/me`
- `/api/conversations`
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

Set `MEDIVOX_DEV_TOKEN` in `.env` before testing the client-secret endpoint.

## Notes

This is a development scaffold, not a production backend.
- Passwords are stored only in memory for local testing.
- Tokens are stored only in memory for local testing.
- Replace the in-memory stores with a proper database and secure authentication before release.
