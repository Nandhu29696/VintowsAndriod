# Vintows Android

Native Android client for the Vintows REST API.
See [`../VINTOWS_ARCHITECTURE.md`](../VINTOWS_ARCHITECTURE.md) for the API reference and [`../ANDROID_BUILD_PLAN.md`](../ANDROID_BUILD_PLAN.md) for the phase plan.

## Requirements

| Tool | Version |
|---|---|
| Android Studio | latest stable (supports AGP 9.4) |
| JDK | 17 |
| Android SDK | platform 37 (compile), 36 (target), min 26 |
| Gradle | 9.6.0 (via wrapper) |

Open this `VintowsAndroid/` folder (not the parent folder) in Android Studio.

## Build flavours

| Flavour | API base URL | App ID |
|---|---|---|
| `dev` | `https://dev.vintows.com/api/v1/` | `com.vintows.app.dev` |
| `qa` | `https://qa.vintows.com/api/v1/` | `com.vintows.app.qa` |
| `prod` | `https://www.vintows.com/api/v1/` | `com.vintows.app` |

All three can be installed side by side. Use **qaDebug** for day-to-day work (Build Variants panel).

```bash
./gradlew assembleQaDebug          # build APK
./gradlew testQaDebugUnitTest      # unit tests
./gradlew installQaDebug           # install on a connected device/emulator
```

## Stack

Kotlin · Jetpack Compose (Material 3) · Hilt · Retrofit + OkHttp + kotlinx.serialization · DataStore · Navigation-Compose (type-safe routes) · Coroutines/Flow

## Structure

```
app/src/main/java/com/vintows/app/
├─ core/
│  ├─ network/       ApiEnvelope, ApiCaller (→ NetworkResult), AuthInterceptor, HealthApi
│  ├─ session/       Session, SessionManager (DataStore), KeystoreTokenCipher, JwtDecoder
│  ├─ di/            AppModule, NetworkModule
│  ├─ designsystem/  VintowsTheme (Plus Jakarta Sans), VCard, SectionHeader, IconBadge, PriceTag, DetailTopBar, chips, states
│  ├─ util/          DateFormatter, initialsOf
│  ├─ ui/            Section (Loading / Loaded / Failed per screen part)
│  ├─ push/          PushManager, VintowsMessagingService, NotificationHelper (active only with google-services.json)
│  └─ rbac/          MenuRepository (roleaccess/get), MenuItem, HomeTab + role → tabs rules
├─ feature/
│  ├─ auth/          Password login, student email-code sign-in and sign-up, AuthRepository
│  ├─ gamification/  Learner dashboard (level, streaks, missions, badges, achievements) and Leaderboard
│  ├─ courses/       Programs → Subject → Chapter → Lesson → Topic, learning materials and viewers
│  ├─ assessments/   Tests tab, test intro, test player (timer, palette, proctoring, resume) and results
│  ├─ notifications/ Notifications list (GET notifications)
│  ├─ home/          App shell: top bar, role-based bottom tabs, Modules tab, Profile tab
│  └─ foundation/    Diagnostics screen (debug builds, opened from Profile)
├─ navigation/       VintowsNavHost (Login ↔ Home driven by the session)
├─ AppViewModel.kt   Restores the session behind the splash screen; exposes Loading/LoggedOut/LoggedIn
├─ MainActivity.kt
└─ VintowsApp.kt
```

New features go in `feature/<name>/` with `data/`, `domain/` and `ui/` sub-packages.

## Conventions

- **Every API call goes through `ApiCaller`.** It unwraps the `{ success, message, data }` envelope and maps failures to `NetworkResult.Error(kind, message)`. Server stack traces are never shown.
- `AuthInterceptor` adds `Authorization: Bearer`, `x-tenant-id` and `x-db-scope`. A 401 clears the session and emits `SessionEvent.Expired`.
- Tokens are encrypted with an Android Keystore AES-GCM key before being written to DataStore.
- `SessionManager.restore()` must run once at startup, before any API call.
- QA is a shared environment: don't create data there without the client's approval. Use MockWebServer tests instead.

## Live smoke test (optional)

`LiveQaSmokeTest` runs the real login + menu code against the flavour's server. It is skipped unless credentials are given as environment variables, so they never live in the repo:

```bash
VINTOWS_TEST_EMAIL=you@example.com VINTOWS_TEST_PASSWORD=…   ./gradlew testQaDebugUnitTest --tests "*LiveQaSmokeTest*" -i
```

It only calls `POST auth/login` and `GET roleaccess/get`.

## JVM tests and DataStore

Unit tests use `InMemoryPreferencesDataStore`: the file-backed DataStore fails on Windows JVMs on the second write (temp-file rename). The app on Android is unaffected.

## Known environment note

On this machine Gradle's Java downloader times out reaching `services.gradle.org`. The 9.6.0 distribution was fetched with curl and its SHA-256 is pinned in `gradle/wrapper/gradle-wrapper.properties`.

## Fonts

Plus Jakarta Sans (`res/font/plus_jakarta_sans.ttf`) is licensed under the SIL Open Font License 1.1; the licence ships in `assets/licenses/`.
