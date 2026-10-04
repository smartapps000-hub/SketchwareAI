# Sign-in gate and telemetry: design review (before any code)

Asked for in the directive ("Before coding, challenge this design"). Status: **nothing of this is built.**
What is needed to start is at the end.

## 1. What blocks Phase 1 (Credential Manager + Firebase Auth) today

1. **No Firebase configuration.** Only the Web client ID was supplied. FirebaseAuth cannot start without the
   project's `google-services.json` (API key, app id `1:596456499260:android:...`, project id) for an Android app
   registered with package `com.sketchware.ai`. The Web client ID alone is enough for neither Firebase nor
   Credential Manager's Android-side check.
2. **The signing key is the public AOSP test key** (`testkey.keystore`, CN=Android, SHA-1
   `61:ED:37:7E:85:D3:86:A8:DF:EE:6B:86:4B:D8:5B:0B:FA:A5:AF:81`). Its private key is published with AOSP and
   with the upstream repository. Registering that fingerprint for Google Sign-In would let anyone build an app
   that passes as `com.sketchware.ai`, and anyone can sign an "update" users' phones will accept. A public
   release needs its own private keystore first; the fingerprint to register in Firebase is that keystore's.
   Changing the key means the test-key build must be uninstalled once (projects in `/.sketchware` stay).
3. **The app's AndroidX is not a Gradle dependency.** It is a 2021 AndroidX bundle inside
   `app/libs/androidx-classes.jar`, with its resources merged into `res/` under pinned ids, and the
   precompiled Sketchware classes are linked against exactly that. `androidx.credentials`,
   `credentials-play-services-auth`, `googleid` and `firebase-auth` bring current `androidx.core`,
   `fragment`, `activity`, `lifecycle`, `collection`, Kotlin 1.8+ and coroutines, and need compileSdk 34
   (the app compiles with 30 on AGP 7.3.1). Adding them as they are gives duplicate-class errors; excluding
   their AndroidX makes them run against a five-year-old AndroidX they were not built for. So Phase 1 starts
   with a toolchain step (AGP 8 / compileSdk 34 / real AndroidX), which is its own release with its own
   regression run, not a patch next to the build engine.
4. **A mandatory gate that cannot be tested here.** No Play services, no device, no emulator in the
   workspace. A gate at app start that fails on the phone locks the whole IDE. It has to ship behind a
   switch first (sign-in offered, not required), be confirmed on the phone, and only then become mandatory.

## 2. Risks in the design as written

**Login compatibility**
- Credential Manager's Google button needs Google Play services. Phones without it (Huawei, de-Googled
  ROMs, some emulators) can never pass a mandatory gate. Decide: block them, or allow "continue without
  account" with telemetry off.
- First sign-in needs internet; an offline first launch (the app's main selling point is offline use)
  shows a wall. The wall must say why and must not appear again once signed in.
- `FirebaseAuth.getCurrentUser()` is non-null offline after a successful sign-in, so the offline policy in
  the directive works. Token refresh failures while offline must not sign the user out; only
  `ERROR_USER_DISABLED` / `ERROR_USER_NOT_FOUND` / explicit sign-out should.

**Offline-mode conflicts**
- The directive's own rule ("core must be 100% offline") and a mandatory account conflict on first run and
  on reinstall. Stated plainly in the UI is enough; hiding it is not.

**Privacy and PII**
- Email and display name must stay in Firebase Auth only. Analytics user id must be a random installation
  id, not the Firebase UID, unless support needs per-account lookup; if it does, say so in the privacy text.
- Project names, package names, block text, file paths (they contain the user's folder and project names),
  compiler messages with identifiers, and library coordinates of private libraries are all personal or
  confidential data. Error fingerprints must hash the *normalised* message (identifiers and paths removed
  before hashing), or the hash is a stable id for a user's own code.
- Device model + API + RAM bucket + screen bucket + first-open day is close to a fingerprint for a small
  user base. Keep the buckets coarse.
- A telemetry switch and a consent screen are needed before the first event, not only a settings page;
  India's DPDP Act and Google Play's User Data policy both expect notice and consent for this (to be checked
  against their current text).
- Crashlytics and Performance collect by default as soon as they are in the APK; both must be disabled in
  the manifest and enabled in code after consent.

**Analytics cardinality**
- Firebase Analytics: 500 distinct event names, 25 parameters per event, and custom dimensions are limited
  (50 event-scoped). `block_type_family`, `component_type`, `tool_name`, `strategy_id`,
  `error_family` must be small fixed vocabularies. Free-text or library coordinates as parameter values end
  in "(other)". The directive's schema is right to use few events; the value lists need to be frozen in code.

**Quota (Spark plan)**
- Firestore free tier: 20,000 writes and 50,000 reads per day. One profile write per sign-in and one
  explicit bug report now and then is fine. Per-build or per-session documents are not (assuming 15 builds
  and 2 sessions per active user per day; an estimate, not measured):

  | active users/day | builds/user/day | one doc per build | one summary doc per session |
  |---|---|---|---|
  | 1,000 | 15 | 15,000 (75% of quota) | ~2,000 |
  | 5,000 | 15 | 75,000 (over) | ~10,000 |
  | 10,000 | 15 | 150,000 (over) | ~20,000 (at the limit) |

  So build/repair telemetry goes to Analytics events (no per-event cost) with local aggregation, exactly as
  the directive says, and Firestore keeps only profile metadata and explicit reports.
- BigQuery: the free sandbox keeps tables for 60 days and has no streaming export; more than that needs the
  paid Blaze plan. Check the current terms before building the Looker/Sheets pipeline on it.
- Realtime Database: 100 simultaneous connections on Spark. Not used.

**Dashboard scaling**
- Analytics' own reports answer most of section 27 without BigQuery up to a few thousand users. The
  "regression radar" (failure rate per engine version) needs custom definitions registered in advance.

**App Check**
- On Android the production provider is Play Integrity. It vouches for builds signed with the registered
  key and, by default, recognised by Google Play; a sideloaded APK signed with the public test key gets no
  valid token. App Check therefore comes after the release key and the distribution decision, not before.

## 3. Things the directive gets right and should stay

Few reusable events with parameters; no source, XML or block text uploaded; normalised fingerprints;
verified-only repair ranking; an explicit, previewed, redacted "send full report"; not claiming the gate is
unbypassable; phases, not one patch.

## 4. Distribution

The upstream LICENSE says the code is source-available for personal use and that the `minApi26` flavor (the
one shipped) is a derivative of GPL-3.0 modules (kotlinc, build-logic from CodeAssist). Publishing this APK
to other people means offering its source under GPL-3.0, and the non-GPL part has no licence that grants
redistribution. This is a legal question, not an engineering one; it should be checked before a public
launch with accounts and telemetry attached.

## 5. What is needed to start Phase 1

1. `google-services.json` of the Firebase project, with an Android app for package `com.sketchware.ai`.
2. A decision on the release keystore (a new private key; who keeps it), and its SHA-1/SHA-256 registered in
   Firebase and in the Google Cloud OAuth consent screen.
3. A decision for phones without Play services: blocked, or "continue without account".
4. Agreement that the first release with sign-in is optional sign-in, made mandatory after it is confirmed
   on the phone.
5. A release slot for the toolchain step (AGP 8, compileSdk 34, AndroidX as dependencies) before it.
