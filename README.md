# MediVox AI Assistant

MediVox is an Android-first, voice-led conversational health information assistant. The intended experience is a natural, full-duplex conversation similar to ChatGPT Voice Mode, using OpenAI Realtime over WebRTC. The first product version does not use medical RAG; retrieval and clinical knowledge sources are planned for a later, separately controlled component.

> **Repository status:** the current main branch contains only an initial Kotlin/Jetpack Compose screen and architecture notes. It does not yet contain a complete Android Gradle build, a backend, authentication, WebRTC transport, or conversation persistence. This README distinguishes what exists today from what becomes runnable after the missing build foundation is added.

## Repository layout

### Current checked-in layout

~~~text
medivox-ai-assistant/
├── README.md
├── android-app/
│   ├── settings.gradle.kts
│   └── app/
│       └── src/
│           └── main/
│               └── java/
│                   └── com/
│                       └── medivox/
│                           └── ai/
│                               └── MainActivity.kt
└── docs/
    ├── ARCHITECTURE.md
    ├── DEVELOPMENT_PLAN.md
    └── TESTING.md
~~~

The root developer.md file is the engineering roadmap and progress log. It is the single source of truth for planned work, implementation status, decisions, and acceptance criteria.

### Intended application layout

The following is the target structure as the codebase grows. Items marked planned are not necessarily present in the current checkout.

~~~text
medivox-ai-assistant/
├── README.md
├── developer.md
│
├── android-app/
│   ├── settings.gradle.kts
│   ├── build.gradle.kts                 # planned: root Android build
│   ├── gradle.properties                # planned
│   ├── gradlew / gradlew.bat             # planned: Gradle wrapper
│   └── app/
│       ├── build.gradle.kts             # planned: app plugins/dependencies
│       └── src/
│           ├── main/
│           │   ├── AndroidManifest.xml  # planned
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
├── backend/                              # planned: secure API service
│   ├── app/
│   │   ├── api/
│   │   │   ├── auth.py
│   │   │   ├── realtime.py
│   │   │   └── conversations.py
│   │   ├── core/
│   │   │   ├── config.py
│   │   │   └── security.py
│   │   ├── db/
│   │   ├── models/
│   │   └── main.py
│   ├── tests/
│   ├── requirements.txt
│   └── .env.example
│
├── rag/                                   # planned; disabled in initial MVP
│   ├── ingestion/
│   ├── retrieval/
│   ├── embeddings/
│   └── evaluation/
│
└── docs/
    ├── ARCHITECTURE.md
    └── TESTING.md
~~~

## Technology choices

| Area | Intended technology | Purpose |
|---|---|---|
| Android | Kotlin | Native application language |
| UI | Jetpack Compose + Material 3 | Declarative, animated mobile interface |
| Architecture | MVVM with domain/data separation | Testability and maintainability |
| Voice transport | WebRTC | Bidirectional, low-latency audio |
| AI | OpenAI Realtime API | Real-time speech-to-speech conversation |
| Session events | WebRTC data channel | Realtime event and transcript handling |
| Backend | FastAPI (planned) | Authentication, client-secret issuance, user data |
| Persistence | PostgreSQL (planned) | Accounts, conversations, and message metadata |
| Medical retrieval | Qdrant or another vector store (future) | Optional RAG retrieval; not used in the first MVP |

MediVox does not use a local audio-file recorder or an upload-a-file voice pipeline. Android's WebRTC audio device module and media tracks are intended to carry live microphone and speaker audio. The waveform is a UI visualization; unless connected to audio-level statistics, it must not be described as a measured microphone waveform.

## Requirements

For Android development:

- Android Studio with a compatible Android Gradle Plugin
- JDK 17
- Android SDK Platform 35 (or the compile SDK selected in the app build)
- Android emulator or physical Android device
- Git

For the future backend:

- Python 3.11+
- An OpenAI API project with access to the selected Realtime model
- A server-side environment for secrets (never commit API keys)

## Build status and how to run

### Important: current checkout is not yet buildable

At the time this README was updated, the repository is missing the Android root build.gradle.kts, app/build.gradle.kts, AndroidManifest.xml, resources, and Gradle wrapper scripts. settings.gradle.kts declares the :app module, but that alone is not enough for Android Studio or Gradle to assemble an APK.

Therefore, there is no honest assembleDebug or install command that will work against the current main branch yet. Completing the Gradle application skeleton is the first build task. Do not add an OpenAI key to the Android app while doing this.

### Once the Android Gradle files are present

Open the android-app/ directory in Android Studio, allow Gradle sync to complete, select an emulator/device, and run the app configuration.

From a terminal, after the Gradle wrapper has been added:

~~~bash
cd android-app
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:installDebug
~~~

On Windows, use gradlew.bat in place of ./gradlew.

The debug APK is normally generated under:

~~~text
android-app/app/build/outputs/apk/debug/app-debug.apk
~~~

### Backend setup (when backend files are added)

The current repository does not yet contain the backend service. When the backend implementation is merged, the expected local workflow will be:

~~~bash
cd backend
python -m venv .venv

# macOS / Linux
source .venv/bin/activate

# Windows PowerShell
# .venv\Scripts\Activate.ps1

pip install -r requirements.txt
cp .env.example .env
~~~

Set the required values in backend/.env, then start the service using the command specified in backend/README.md. Never commit .env.

For Android Emulator, the host machine is commonly reachable at http://10.0.2.2:<port>. For a physical device, use the development machine's LAN IP and keep both devices on a trusted network. Any non-production HTTP allowance must be limited to debug builds; production traffic must use HTTPS.

## Testing checklist

Use this checklist as the project becomes runnable.

### Android build and launch

- [ ] Gradle sync succeeds without unresolved dependencies.
- [ ] assembleDebug succeeds.
- [ ] App installs and opens on an emulator.
- [ ] App installs and opens on a physical Android device.
- [ ] Rotation, background/foreground, and process recreation do not crash the app.

### Account and backend (when implemented)

- [ ] Registration validates email and password input.
- [ ] Login returns a user session.
- [ ] Invalid credentials produce a safe, understandable error.
- [ ] A user can only read their own profile and conversations.
- [ ] Expired/revoked tokens are rejected.
- [ ] API keys and passwords never appear in Android resources, logs, or Git.

### Realtime voice (when implemented)

- [ ] Microphone permission is requested only when starting a voice session.
- [ ] Denying permission leaves the app usable and explains why voice cannot start.
- [ ] A WebRTC connection is established through the backend-issued short-lived client secret.
- [ ] Audio flows in both directions without creating an audio file.
- [ ] Server VAD identifies user speech and turn boundaries.
- [ ] The user can interrupt assistant speech naturally (barge-in).
- [ ] The UI accurately displays connecting, listening, thinking, speaking, and disconnected states.
- [ ] Ending the session closes tracks, data channel, peer connection, and audio routing.
- [ ] Network loss and backend errors produce recoverable messages.

### Conversation history and future RAG

- [ ] Final transcript messages are stored against the authenticated user.
- [ ] A user cannot access another user's conversation.
- [ ] Deleting a conversation removes its messages according to the retention policy.
- [ ] RAG remains disabled until the medical corpus, provenance, evaluation, and safety controls are ready.

## Security and medical-use notes

- Never embed a permanent OpenAI API key in the APK, source code, BuildConfig, or a committed properties file.
- The backend should issue a short-lived Realtime client secret only after authenticating the user and applying rate/usage controls.
- Use HTTPS in production and never rely on a shared static token embedded in the mobile binary as production authentication.
- Avoid logging raw audio, sensitive health details, credentials, or full client secrets.
- Treat conversation content as sensitive personal information; define retention, deletion, access control, and consent before launch.
- MediVox is an AI health information prototype, not a substitute for a licensed clinician, emergency services, or an in-person medical assessment.

## Project progress

For the authoritative implementation status, next tasks, design decisions, and phase acceptance criteria, see developer.md.
