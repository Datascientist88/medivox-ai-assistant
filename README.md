# MediVox AI Assistant

MediVox is an Android-first, voice-led conversational health assistant. The intended experience is a natural, full-duplex conversation similar to ChatGPT Voice Mode, using OpenAI Realtime over WebRTC. The current branch now includes a buildable Android application skeleton, a demo auth/voice UI, and a backend scaffold for local testing.

## Repository structure

### What is currently in the repository

```text
medivox-ai-assistant/
├── README.md
├── developer.md
├── android-app/
│   ├── build.gradle.kts
│   ├── gradle.properties
│   ├── settings.gradle.kts
│   └── app/
│       ├── build.gradle.kts
│       ├── proguard-rules.pro
│       └── src/
│           └── main/
│               ├── AndroidManifest.xml
│               ├── java/com/medivox/ai/
│               │   ├── MainActivity.kt
│               │   ├── data/Models.kt
│               │   ├── presentation/
│               │   │   ├── AuthViewModel.kt
│               │   │   └── VoiceViewModel.kt
│               │   ├── ui/Screens.kt
│               │   ├── ui/theme/MediVoxTheme.kt
│               │   └── voice/VoiceModels.kt
│               └── res/values/
│                   ├── strings.xml
│                   └── themes.xml
├── backend/
│   ├── app/main.py
│   ├── requirements.txt
│   ├── .env.example
│   └── README.md
└── docs/
    ├── ARCHITECTURE.md
    └── TESTING.md
```

### Planned expansion areas

```text
backend/
├── app/api/
├── app/core/
├── app/db/
├── app/models/
└── tests/

rag/
├── ingestion/
├── retrieval/
├── embeddings/
└── evaluation/
```

## What the current app does

The Android app currently launches into:

- a demo sign-in / register screen,
- a voice screen with animated orb and waveform visualization,
- a local demo conversation flow,
- a clean foundation for the future WebRTC + OpenAI Realtime implementation.

The backend scaffold currently provides:

- `/health`
- `/auth/register`
- `/auth/login`
- `/auth/me`
- `/api/conversations`
- `/api/conversations/{conversation_id}/messages`
- `/api/realtime/client-secret`

## Requirements

- Android Studio with a compatible Android Gradle Plugin
- JDK 17
- Android SDK Platform 35
- An Android emulator or physical Android device
- Python 3.11+ for the backend scaffold

## How to run

### Android app

Open the `android-app/` folder in Android Studio and let Gradle sync complete. Then run the `app` configuration on an emulator or device.

If you have Gradle installed locally, you can also build from a terminal:

```bash
cd android-app
gradle :app:assembleDebug
```

The app currently opens to a demo auth + voice experience. The real OpenAI Realtime WebRTC path is still under development.

### Backend scaffold

```bash
cd backend
python -m venv .venv
source .venv/bin/activate   # macOS / Linux
# .venv\Scripts\Activate.ps1  # Windows PowerShell
pip install -r requirements.txt
cp .env.example .env
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

Set `MEDIVOX_DEV_TOKEN` in `backend/.env` before testing the development-gated realtime endpoint.

## Testing checklist

### Android

- App opens successfully in Android Studio.
- Demo sign-in/register flows update the UI.
- Voice screen opens after sign-in.
- Animated orb and waveform render correctly.
- Demo conversation entry updates the transcript list.

### Backend

- `GET /health` returns an OK response.
- Register/login/me return a user session in local development.
- Conversation creation and message posting work for the same bearer token.
- `/api/realtime/client-secret` rejects requests without the development token.

## Security notes

- Do not commit secrets, tokens, or passwords.
- The backend scaffold is for local development only.
- Replace in-memory auth and conversation storage with a real database and proper authentication before release.
- Keep the permanent OpenAI API key server-side only.
- Do not treat the demo UI as production authentication or production voice transport.

## Project coordination

The authoritative engineering roadmap, current phase status, and session log live in [`developer.md`](developer.md).
