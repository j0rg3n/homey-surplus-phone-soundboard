# CLAUDE.md — Development Rules

This file contains standing instructions for how to work on this codebase.  
Read this before making any changes. These rules apply to both the Homey (Node.js) and Android (Kotlin) sides of the project.

---

## TODO.md Rules

- **Keep TODO.md lean.** Each item should be one sentence describing *what* to build and *what to test*. Protocol details, message shapes, and interface signatures live in SPEC.md — reference by section number (e.g. "per SPEC §4.5"), do not repeat.
- **Preserve the group structure.** Groups must be completed in order. Within a group, items are independent and **can be implemented in parallel** — do not introduce hidden dependencies between sibling items.
- **Mark items done** by changing `- [ ]` to `- [x]`. Do not delete completed items.
- **Add new items at the bottom of the appropriate group.** Do not retrofit completed items.
- **If SPEC is incomplete, fix SPEC first.** If a TODO item needs to explain how something works, that belongs in SPEC.md, not the TODO.

---

## Testing Rules

### Coverage
- Homey (Node.js) overall: **> 65%**
- Android overall: **> 65%**
- `MessageProtocol` (both sides) and `PlaybackHandleStore`: **> 80%**
- Do not consider a group done until its coverage target is met.
- Run `npm run coverage` (Homey) and `./gradlew testDebugUnitTestCoverage` (Android) before marking a group complete.

### Every step must be independently testable
- Each TODO item must produce at least one test file or add meaningful tests to an existing one.
- If a module cannot be tested without a real device, a running Homey, or a real Android device, the architecture is wrong — fix it first.

### Mocking — Homey side
- Use `MockWebSocketClient` to simulate all WebSocket interactions. No real WebSocket server in unit tests.
- Use `MockHomeyDevice` to simulate all Homey SDK calls. No running Homey instance in tests.
- Keep mocks honest: they must implement the same interface as the real thing. If the real interface changes, update the mock immediately.
- Assert on outgoing messages via `mock.getLastSentMessage()` / `mock.getSentMessages()`. Do not inspect internal device state directly.

### Mocking — Android side
- Mock `AudioTrack` and `MediaExtractor` in unit tests for `PlaybackService` logic. Never depend on a real audio device in unit tests.
- Use Ktor's built-in test engine (`testApplication`) for WebSocket server integration tests — no real network socket needed.
- Use Room's in-memory database for `SampleRepository` tests.
- Espresso tests run on an emulator or device — these are UI tests and are allowed to require the Android runtime.

### Web / pairing UI
- Homey pairing views must be tested with **Playwright** (`test/e2e/`).
- Playwright tests must run headless in CI.
- Cover at minimum: happy path (device found and connected), timeout/error display, retry flow.

### Test file naming — Homey
- Unit tests: `test/<ModuleName>.test.js`
- E2E tests: `test/e2e/<feature>.spec.js`
- Mocks: `test/mocks/<MockName>.js`

### Test file naming — Android
- Unit tests: `app/src/test/java/…/<ClassName>Test.kt`
- UI tests: `app/src/androidTest/java/…/<ClassName>Test.kt`

### Test style
- Use descriptive test names: `'fires sound_done with reason connection_lost for all in-flight handles on disconnect'`.
- Group related tests with `describe` (JS) or `@Nested` (Kotlin).
- For protocol tests, assert on exact field values — do not use snapshot tests for message shapes.

---

## Architecture Rules

### Homey side
- **`lib/` has no Homey SDK imports.** Only `drivers/` touches Homey SDK.
- **`device.js` must remain thin.** Business logic that creeps into `device.js` belongs in a `lib/` module.
- **`constants.js` is the single source of truth** for all message type strings, error codes, and numeric defaults. No magic strings or numbers outside `constants.js` and tests.
- **`MessageProtocol.js` is stateless.** It only serializes and validates — no side effects.

### Android side
- **`PlaybackService` owns the audio engine and WebSocket server.** UI components observe state via StateFlow from repositories; they do not interact with audio or network directly.
- **Repository layer is the only path to the database.** Activities, Fragments, and ViewModels never access Room DAOs directly.
- **All WebSocket message handling goes through `MessageProtocol`.** Do not parse JSON inline in service or server code.
- **`constants.kt` must stay in sync with `constants.js`.** When a message type or error code changes, update both files in the same commit.

---

## Protocol Compatibility Rules

- The `version` field in `hello` and `hello_ack` is `"1.0"` for this implementation.
- If a breaking protocol change is needed, bump the version and add a negotiation step — do not silently change message shapes.
- Unknown message types received by either side must respond with `error` type `UNKNOWN_MESSAGE` and continue running — never crash on an unknown message.

---

## SPEC.md Rules

- SPEC.md is the source of truth for interfaces, message shapes, behavior, and data models.
- If implementation reveals the SPEC is wrong or incomplete, **update SPEC.md first**, then implement.
- Do not silently deviate from SPEC — note the reason when updating it.

---

## Homey SDK — Known Gotchas

### Running the app
- Use `homey app run --remote` for rapid iteration — **not** `homey app install`.
- Smoke test with timestamps: `timeout 10 homey app run --remote | ts`
- `console.log()` output appears in Homey web UI → More → Logs.
- **Always ask the user to verify UI behaviour** — Flow cards, capabilities, and pairing views cannot be tested headlessly.

### Flow Triggers (SDK v3)
- `getTriggerCard(id)` → `FlowCardTrigger` — call `.trigger(tokens)` (no device arg).
- `getDeviceTriggerCard(id)` → `FlowCardTriggerDevice` — call `.trigger(device, tokens)`.
- Never pass a device to `getTriggerCard().trigger()` — it silently fires on the wrong scope.
- Device-scoped trigger cards: do NOT use `"device": true`. Use an explicit `args` entry instead:
  ```json
  "args": [{ "type": "device", "name": "device", "filter": "driver_id=<id>" }]
  ```
  Using `"device": true` causes `.trigger()` to resolve without error but flows never execute.

### Capabilities
- Re-pair the device after adding or removing capabilities — the Homey UI does not update live.
- Changing capability order also requires re-pairing to take effect.

## Android — Deploying to a Physical Device

- `adb install` over wireless debugging silently fails with no useful error. Always use the two-step approach:
  ```
  adb push <apk> /data/local/tmp/soundboard.apk
  adb shell pm install -r /data/local/tmp/soundboard.apk
  ```
- Wireless ADB connections drop when the screen locks. Run `adb connect <ip>:<port>` again if the device disappears from `adb devices`.
- After install, force-stop and relaunch to ensure the new code runs:
  ```
  adb shell am force-stop com.soundboard
  adb shell am start -n com.soundboard/.ui.MainActivity
  ```
