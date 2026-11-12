# MediVox Developer Guide

> Purpose: this is the working engineering plan and progress ledger for MediVox. Update this file whenever repository work is completed, tested, deferred, or redesigned. Keep the status factual.

Last reviewed: 2026-11-12
Default branch: main
Repository: https://github.com/Datascientist88/medivox-ai-assistant

---

## 1. Product definition

MediVox is an Android-first conversational AI health information assistant. Its primary experience is natural, full-duplex voice conversation similar in interaction style to ChatGPT Voice Mode.

### Product constraints

- Voice-first experience; text transcript is supportive, not the primary interaction.
- Use OpenAI Realtime with WebRTC for real-time media transport.
- Do not build an audio-file recorder/upload pipeline.
- Android WebRTC media tracks handle microphone input and assistant audio output.
- WebRTC data channel handles session events and transcript/status events.
- RAG is deliberately out of scope for the first conversational MVP.
- The assistant must be clear that it provides general health information, not a definitive diagnosis.
- Urgent and emergency symptoms must route users to timely in-person/emergency care.
- The permanent OpenAI API key must remain server-side. The mobile app receives only a short-lived client secret after server-side user authentication.

---

## 2. Current repository status

### Verified state of main at the latest review

| Item | Status |
|---|---|
| Root README | Present and updated |
| Developer roadmap | Present |
| Android root build file | Present |
| Android app build file | Present |
| Android manifest/resources/theme | Present |
| Android app auth/voice UI | Present |
| Android voice client | Present in source |
| Android backend API/session storage layer | Present in source |
| Backend scaffold | Present |
| Realtime client-secret flow | Present in source |
| Production authentication | Not yet implemented |
| Persistent conversation storage | Present for development/local testing |
| Real WebRTC voice transport | Present in source; not yet build-verified in this session |
| RAG pipeline | Not implemented; intentionally deferred |

### Current milestone

Status: the Android and backend foundations are in place in source; the next priority is build verification, then tightening the production auth and voice transport path.

---

## 3. Target project structure

~~~text
medivox-ai-assistant/
├── README.md
├── developer.md
├── android-app/
│   ├── build.gradle.kts
│   ├── gradle.properties
│   ├── settings.gradle.kts
│   └── app/
│       ├── build.gradle.kts
│       └── src/
│           └── main/
│               ├── AndroidManifest.xml
│               ├── res/
│               └── java/com/medivox/ai/
│                   ├── MainActivity.kt
│                   ├── data/
│                   ├── presentation/
│                   ├── ui/
│                   └── voice/
├── backend/
│   ├── app/
│   │   └── main.py
│   ├── requirements.txt
│   ├── .env.example
│   └── README.md
├── rag/
└── docs/
    ├── ARCHITECTURE.md
    └── TESTING.md
~~~

---

## 4. Architecture decisions

### Android client

- Kotlin + Jetpack Compose + Material 3.
- MVVM with state exposed as StateFlow.
- Keep UI composables free of WebRTC, network, and persistence implementation details.
- Place WebRTC session lifecycle in a dedicated voice/data layer.
- Use a single source of truth for the voice connection state.
- Stop/dispose all media resources when a session ends or the app is torn down.

### Realtime voice

Expected connection sequence:

1. User taps the voice control and grants microphone permission.
2. Android authenticates the user with the MediVox backend.
3. Backend requests a short-lived OpenAI Realtime client secret using the server-side API key.
4. Android creates a WebRTC PeerConnection, a microphone audio track, and a data channel.
5. Android creates an SDP offer and exchanges it with the OpenAI Realtime calls endpoint using the short-lived secret.
6. Android applies the SDP answer and receives the assistant audio track.
7. Audio remains on WebRTC media tracks; Realtime events flow over the data channel.
8. Server VAD and the Realtime model manage turn boundaries and interruption behavior.
9. UI reflects connection, listening, user speaking, thinking, assistant speaking, and error/disconnected states.

No local WAV/MP3 recording, file upload, or separate STT → text model → TTS chain.

### Backend and security

- Own user authentication and authorization.
- Validate the user's access token before creating Realtime client secrets.
- Keep OpenAI API key in server-side secrets/environment configuration only.
- Enforce quotas/rate limits and request logging that excludes secrets and sensitive health content.
- Apply user-level access control to conversation records.
- Production endpoints must use HTTPS.
- A static shared development token embedded in an APK is not production authentication.

### Medical RAG (later)

- Keep retrieval disabled in the initial voice MVP.
- Medical sources must have provenance, version, jurisdiction, review date, and usage rights.
- Add evaluation for citation fidelity, retrieval precision, unsafe omissions, and stale guidance.
- Do not invent citations or present generated answers as sourced clinical recommendations.

---

## 5. Development phases and acceptance criteria

### Phase 0 — Buildable Android foundation

Status: In progress

Deliverables:
- Android Gradle configuration.
- Manifest, resources, and theme.
- Launchable Compose app.
- Demo auth and voice screens.
- Repo hygiene files.

Acceptance:
- Android Studio syncs from a clean clone.
- App launches on emulator/device.
- No secrets are committed.

### Phase 1 — Voice-first experience and design system

Status: In progress

Deliverables:
- MediVox visual identity and dark Material 3 theme.
- Full-screen conversational voice screen.
- Animated orb/core and waveform visualization.
- Clear idle, connecting, listening, user-speaking, thinking, assistant-speaking, and error states.
- Microphone permission UX and graceful denial handling.

Acceptance:
- UI can be exercised in a demo mode without a backend.
- Animation state changes are deterministic and testable.
- Permission can be granted/denied/revoked without app crash.

### Phase 2 — OpenAI Realtime WebRTC voice engine

Status: In progress

Deliverables:
- Backend-issued short-lived Realtime client secret.
- Android WebRTC peer connection and microphone/remote audio tracks.
- SDP offer/answer exchange.
- Realtime event data channel.
- Server VAD and interruption/barge-in behavior.
- Audio route selection and cleanup.

Acceptance:
- Two-way speech works on at least one emulator and one physical device.
- No audio file is created or uploaded.
- User can interrupt the assistant while it is speaking.

### Phase 3 — Accounts, profiles, and conversation history

Status: In progress for local development

Deliverables:
- Registration and login.
- Secure token storage on device.
- User profile and preferences.
- Backend access-token validation and per-user authorization.
- Conversation creation, listing, message persistence, and deletion.

Acceptance:
- Users can register, sign in, and sign out.
- User A cannot read or modify User B's profile or conversations.
- Passwords are never stored in plaintext.

### Phase 4 — Reliability, quality, and personalization

Status: Not started

Deliverables:
- Device/network compatibility improvements.
- TURN support/configuration for restrictive networks when required.
- Voice/language preferences.
- Unit, integration, and UI tests.
- Accessibility and performance review.

### Phase 5 — Medical RAG foundation (opt-in, separately gated)

Status: Deferred; not part of initial MVP

Deliverables:
- Medical document ingestion and metadata/provenance model.
- Document parsing, chunking, embedding, and indexing.
- Vector store integration.
- Retrieval API and citation metadata.
- Evaluation set and clinician review workflow.

---

## 6. Current work queue

Prioritized next tasks:

1. Run a clean Android build and resolve any compile-time issues.
2. Verify the backend auth and local persistence flow end-to-end.
3. Harden the OpenAI Realtime client-secret flow for production authentication.
4. Tighten the voice session cleanup/reconnect behavior.
5. Add build/test automation.

Do not start RAG implementation before the voice MVP and safety/privacy foundations are stable.

---

## 7. Run commands

### Android

Open `android-app/` in Android Studio and run the `app` configuration.

If Gradle is available locally:

~~~bash
cd android-app
gradle :app:assembleDebug
~~~

### Backend

~~~bash
cd backend
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
~~~

---

## 8. Engineering log

### 2026-11-11 — Repository review and documentation alignment
- Inspected the initial tree.
- Established the roadmap file.

### 2026-11-12 — Buildable Android foundation and backend scaffold
- Added Android Gradle configuration and build properties.
- Added manifest, theme, resources, demo auth screen, demo voice screen, and view models.
- Added backend scaffold with auth/conversation/client-secret endpoints.
- Updated README to match the current repository structure.
- Tests run: not yet executed in this session.
- Known issues: real WebRTC transport, persistent auth, and persistent conversation storage are still pending.
- Next: wire real WebRTC voice transport and replace the demo auth/session flow.

### 2026-11-12 — Realtime voice and backend auth wiring
- Added backend signed-token security helpers, local persistent storage, and authenticated Realtime client-secret issuance.
- Added Android backend API, secure session storage, auth repository, conversation repository, and WebRTC realtime client.
- Updated the Android voice view model to use the backend-backed flow.
- Removed duplicate Android data models.
- Added the backend state file to `.gitignore`.
- Tests run: not yet executed in this session.
- Known issues: no clean build/test verification yet; production auth and release hardening are still pending.
- Next: run a clean compile and fix any remaining build errors, then exercise the backend and realtime flows locally.
