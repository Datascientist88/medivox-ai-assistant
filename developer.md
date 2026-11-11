# MediVox Developer Guide

> Purpose: This is the working engineering plan and progress ledger for MediVox. Update this file whenever repository work is completed, tested, deferred, or redesigned. Keep the status factual and link to the commits or pull requests that prove completed work.

Last reviewed: 2026-11-11
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
- RAG is deliberately out of scope for the first conversational MVP. Keep clean extension points, but do not retrieve medical documents or present RAG as active until separately enabled and evaluated.
- The assistant must be clear that it provides general health information, not a definitive diagnosis.
- Urgent and emergency symptoms must route users to timely in-person/emergency care.
- The permanent OpenAI API key must remain server-side. The mobile app receives only a short-lived client secret after server-side user authentication.

## 2. Current repository status

### Verified state of main at last review

The repository was inspected directly before this document was written.

| Item | Verified status |
|---|---|
| Root README | Present |
| Android settings file | Present; declares the :app module |
| Kotlin MainActivity | Present; displays a basic Compose placeholder |
| Android root/app Gradle build files | Missing |
| Gradle wrapper | Missing |
| Android manifest/resources | Missing |
| Android build currently reproducible | No; required build files are missing |
| WebRTC implementation | Not present in the verified tree |
| OpenAI Realtime backend/token service | Not present in the verified tree |
| Registration/login | Not present |
| Conversation database/history | Not present |
| RAG pipeline | Not present; intentionally deferred |

Earlier conversational notes described some implementation files as completed, but those files were not present on the inspected main branch. They are therefore not counted as delivered. Only merged and verified repository contents should be marked complete.

### Current milestone

Status: initial source placeholder only. The immediate priority is making the Android project buildable, then creating the visible voice UI and the secure Realtime WebRTC path.

---

## 3. Target project structure

This is the intended architecture. Add directories and modules incrementally; do not create empty layers without a concrete use.

~~~text
medivox-ai-assistant/
├── README.md
├── developer.md
│
├── android-app/
│   ├── settings.gradle.kts
│   ├── build.gradle.kts
│   ├── gradle.properties
│   ├── gradlew
│   ├── gradlew.bat
│   └── app/
│       ├── build.gradle.kts
│       └── src/
│           ├── main/
│           │   ├── AndroidManifest.xml
│           │   ├── res/
│           │   └── java/com/medivox/ai/
│           │       ├── MainActivity.kt
│           │       ├── core/
│           │       │   ├── common/
│           │       │   └── network/
│           │       ├── data/
│           │       │   ├── auth/
│           │       │   ├── conversations/
│           │       │   └── realtime/
│           │       ├── domain/
│           │       │   ├── model/
│           │       │   └── repository/
│           │       ├── presentation/
│           │       │   ├── auth/
│           │       │   └── voice/
│           │       ├── ui/
│           │       │   ├── components/
│           │       │   ├── screens/
│           │       │   └── theme/
│           │       └── voice/
│           │           ├── RealtimeSessionManager.kt
│           │           ├── WebRtcClient.kt
│           │           ├── RealtimeEvent.kt
│           │           └── VoiceUiState.kt
│           ├── test/
│           └── androidTest/
│
├── backend/
│   ├── app/
│   │   ├── api/
│   │   │   ├── auth.py
│   │   │   ├── realtime.py
│   │   │   └── conversations.py
│   │   ├── core/
│   │   ├── db/
│   │   ├── models/
│   │   └── main.py
│   ├── tests/
│   ├── requirements.txt
│   └── .env.example
│
├── rag/                       # future; disabled for MVP
│   ├── ingestion/
│   ├── retrieval/
│   ├── embeddings/
│   └── evaluation/
│
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

Statuses use: Not started, In progress, Blocked, Completed. A phase is only Completed when its acceptance criteria pass and the code is in the default branch.

### Phase 0 — Buildable Android foundation

Status: In progress / immediate priority

Deliverables:
- Complete Gradle root and app module configuration.
- Gradle wrapper and Android SDK configuration.
- Android manifest, app theme/resources, package and application ID.
- Basic Compose app screen that launches.
- .gitignore excludes local properties, build outputs, and secrets.
- CI workflow builds a debug APK.

Acceptance:
- Android Studio sync succeeds from a clean clone.
- Debug APK builds on CI and locally.
- App installs and launches on emulator and physical device.
- No secrets are committed.

### Phase 1 — Voice-first experience and design system

Status: Not started

Deliverables:
- MediVox visual identity and dark Material 3 theme.
- Full-screen conversational voice screen.
- Animated orb/core and waveform visualization.
- Clear idle, connecting, listening, user-speaking, thinking, assistant-speaking, and error states.
- Microphone permission UX and graceful denial handling.
- Responsive layout for different screen sizes.
- Accessibility labels, contrast, and reduced-motion consideration.

Acceptance:
- UI can be exercised in a demo mode without a backend.
- Animation state changes are deterministic and testable.
- Permission can be granted/denied/revoked without app crash.
- Visualizer clearly indicates that it is a visualization; do not imply it is a calibrated audio meter until actual audio-level input is wired.

### Phase 2 — OpenAI Realtime WebRTC voice engine

Status: Not started

Deliverables:
- Backend-issued short-lived Realtime client secret.
- Android WebRTC peer connection and microphone/remote audio tracks.
- SDP offer/answer exchange.
- Realtime event data channel.
- Server VAD and interruption/barge-in behavior.
- Audio route selection and cleanup.
- Connection timeout, reconnect, and recoverable error handling.
- Debug-safe network configuration; HTTPS in production.

Acceptance:
- Two-way speech works on at least one emulator and one physical device.
- No audio file is created or uploaded.
- User can interrupt the assistant while it is speaking.
- Connection teardown releases microphone and audio resources.
- Network interruption yields a clear state and allows a clean retry.
- Session secrets are never logged or persisted.

### Phase 3 — Accounts, profiles, and conversation history

Status: Not started

Deliverables:
- Registration and login.
- Secure token storage on device.
- User profile and preferences.
- Backend access-token validation and per-user authorization.
- Conversation creation, listing, message persistence, and deletion.
- Transcript capture from Realtime events (without persisting raw audio by default).
- Sign-out and session expiry handling.

Acceptance:
- Users can register, sign in, and sign out.
- User A cannot read or modify User B's profile or conversations.
- Conversations survive app restart and can be reopened.
- Passwords are never stored in plaintext.
- Data retention and deletion behavior are documented.

### Phase 4 — Reliability, quality, and personalization

Status: Not started

Deliverables:
- Device/network compatibility improvements.
- TURN support/configuration for restrictive networks when required.
- Voice/language preferences.
- Session metrics: connection time, disconnect reason, latency, and errors (privacy-safe).
- Unit, integration, and UI tests.
- Accessibility and performance review.
- Crash reporting and operational monitoring with sensitive-data redaction.

Acceptance:
- Defined test matrix passes on supported Android versions/devices.
- Voice session reliability and latency targets are measured.
- No sensitive transcript/audio is sent to analytics by default.
- Release builds use HTTPS and reviewed permissions.

### Phase 5 — Medical RAG foundation (opt-in, separately gated)

Status: Deferred; not part of initial MVP

Deliverables:
- Medical document ingestion and metadata/provenance model.
- Document parsing, chunking, embedding, and indexing.
- Qdrant or another approved vector store.
- Retrieval API and citation metadata.
- Evaluation set and clinician review workflow.
- Feature flag to keep RAG off until approved.

Acceptance:
- Every retrieved passage has a traceable source and version.
- Answers distinguish retrieved evidence from general model knowledge.
- Retrieval and answer quality pass a documented evaluation.
- Unsupported/out-of-domain questions are handled safely.
- RAG can be disabled without affecting ordinary voice conversation.

### Phase 6 — Multimodal and extended assistant capabilities

Status: Not started

Possible deliverables:
- User-selected image/document sharing.
- Lab report and document explanation with explicit consent.
- More expressive avatar/lip-sync experience.
- Optional handoff to clinician or appointment workflows.
- Localization and additional languages.

Acceptance:
- Each modality has a permission/consent design and retention policy.
- Medical image/document capabilities are clinically evaluated before launch.
- UI presents limitations and uncertainty clearly.

### Phase 7 — Production readiness and release

Status: Not started

Deliverables:
- Threat model and security review.
- Privacy notice, terms, consent, data retention, and deletion workflows.
- API quotas, monitoring, abuse controls, backups, and incident response.
- Store assets, release signing, staged rollout, and support process.
- Accessibility, localization, and final QA.

Acceptance:
- No critical/high security issues remain open.
- Backend secrets and signing credentials are protected.
- Production deployment has monitoring and rollback procedures.
- Legal, privacy, and clinical review gates are complete for intended markets.

---

## 6. Current work queue

Prioritized next tasks:

1. Complete Phase 0: Gradle build scripts, wrapper, app module, manifest, and resources.
2. Add a buildable Compose shell and basic UI tests.
3. Add voice UI and a mock/demo state controller.
4. Implement backend Realtime client-secret issuance with authenticated-user validation.
5. Implement Android WebRTC session lifecycle and event handling.
6. Add auth and per-user conversation persistence.
7. Add device/network testing and CI.

Do not start RAG implementation before the voice MVP and safety/privacy foundations are stable.

---

## 7. Test commands

Once the Android Gradle wrapper and build files exist:

~~~bash
cd android-app
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
~~~

On Windows, use gradlew.bat.

Backend tests (once backend is created):

~~~bash
cd backend
python -m venv .venv
# Activate the environment, then:
pip install -r requirements.txt
pytest
~~~

Run the full manual voice test on both emulator and physical device. Include permission denial, headset/speaker route, background/foreground, interruption, network loss, and reconnect cases.

---

## 8. Engineering log

Append a short entry for every coding session. Never mark work Completed until the code is committed and the relevant test evidence is known.

### 2026-11-11 — Repository review and documentation alignment

- Inspected the current main branch tree and key Android files.
- Confirmed the branch contains a basic Compose placeholder and settings file only.
- Confirmed required Gradle build scripts, wrapper, manifest, backend, WebRTC, authentication, and persistence are not present in the inspected tree.
- Established this file as the single roadmap/status ledger.
- Next: complete the buildable Android foundation before counting voice or auth features as implemented.

### Session update template

~~~text
### YYYY-MM-DD — <short title>
- Added:
- Changed:
- Tests run:
- Test results:
- Commit / PR:
- Known issues:
- Next:
~~~
