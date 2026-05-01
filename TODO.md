# TODO.md — Homey Soundboard

See SPEC.md for all interfaces, protocol details, and data shapes.  
See CLAUDE.md for rules governing how this TODO should be maintained.

---

## Group 1 — Project Scaffolding
*No dependencies. All items parallelisable.*

- [x] **1.1** Initialise Homey app with `homey app create`: id `com.unofficial.homey-soundboard`, name, house-with-sound-waves icon SVG. Verify `homey app validate` passes.
- [x] **1.2** Initialise Android project (Kotlin, min SDK 26, target SDK 34). Add dependencies: Ktor server websockets + CIO, Room, Kotlin coroutines, Hilt. Set up module structure per SPEC §2.1.
- [x] **1.3** Set up Homey test runner (Jest), coverage reporter, `npm test` and `npm run coverage` scripts. Add CI workflow.
- [x] **1.4** Set up Android test configuration: JUnit4 + MockK for unit tests, Espresso for UI tests. Add CI workflow step.
- [x] **1.5** Create `lib/constants.js` (Homey) and `constants.kt` (Android) per SPEC §14. Tests assert all message type strings and numeric constants are defined and match between both sides.
- [x] **1.6** Create `test/mocks/MockWebSocketClient.js` and `test/mocks/MockHomeyDevice.js` per SPEC §12 and §2.2. Tests for mocks themselves: simulate methods trigger callbacks, message capture works.

---

## Group 2 — Protocol Layer
*Depends on Group 1. Items 2.1 and 2.2 are parallelisable.*

- [ ] **2.1** Implement `lib/MessageProtocol.js` (Homey) per SPEC §9. Tests cover: serialize round-trips all message types, deserialize throws on invalid JSON, deserialize throws on missing type, validate catches missing required fields for each message type. Target > 80% coverage.
- [ ] **2.2** Implement Kotlin `MessageProtocol` (Android) — JSON serialization/deserialization for all upstream and downstream message types per SPEC §3.2. Unit tests cover same cases as 2.1 plus Kotlin-side type safety. Target > 80% coverage.

---

## Group 3 — Core Logic
*Depends on Group 2. Items 3.1–3.3 are parallelisable.*

- [ ] **3.1** Implement `lib/PlaybackHandleStore.js` per SPEC §10. Tests cover: add/get/remove lifecycle, `clear()` returns all removed entries, `isPlaying()` returns correct boolean, handles with same soundName counted correctly. Target > 80% coverage.
- [ ] **3.2** Implement `lib/WebSocketClient.js` per SPEC §11. Tests use a mock `ws` server: connect sends `hello`, ping/pong heartbeat fires on interval, 3 missed pongs triggers `onDisconnect`, reconnect backoff doubles up to max, `send()` serializes via `MessageProtocol`. Target > 65% coverage.
- [ ] **3.3** Implement Android `PlaybackService` audio engine (AudioTrack + MediaExtractor/MediaCodec) per SPEC §4. Unit tests with mocked AudioTrack cover: volume mapping formula `(v/100)^1.5` at key values (0, 50, 100, 200, 400), clamp to 1.0, loop stop plays to end of full sample not loop end point, `done` fires with correct reason. Target > 65% coverage.

---

## Group 4 — Network Servers and Clients
*Depends on Group 3. Items 4.1 and 4.2 are parallelisable.*

- [ ] **4.1** Implement Android `WebSocketServer` (Ktor) per SPEC §6.2. Integration tests (Ktor test engine): `hello` → `hello_ack` handshake with sound library, `play` → `started` callback, `stop` → `done` with reason `"stopped"`, `stop_all` stops all instances, `ping` → `pong`, unknown message type → `error` response, `library_update` pushed to all connected sessions on library change.
- [ ] **4.2** Implement Android `SampleRepository`, `PlaybackRepository`, and `FileStore` per SPEC §7. Room unit tests for CRUD. FileStore tests for import (copy to private storage), delete (file removed), and path resolution. PlaybackRepository StateFlow emission tests.

---

## Group 5 — Homey Device Adapter + Android UI
*Depends on Group 4. Items 5.1 and 5.2 are parallelisable.*

- [ ] **5.1** Implement `drivers/soundboard/device.js` per SPEC §8. Tests use `MockWebSocketClient` + `MockHomeyDevice`: `sound_started` trigger fires with correct tokens, `sound_done` fires with reason, `done` fires with `reason: "connection_lost"` for all in-flight handles on disconnect, `speaker_playing` capability toggles correctly, `library_update` updates sound store, `play_sound` action sends correct play message and sets handle tag.
- [ ] **5.2** Implement Android `NowPlayingFragment` and `LibraryFragment` per SPEC §5.2–5.4. Espresso tests: Now Playing list shows active playbacks and stop button triggers stop; Library search filters by name; Sample edit sheet saves name/volume/loop fields; delete shows confirmation dialog.

---

## Group 6 — Pairing + Settings + Flow Cards
*Depends on Group 5. Items parallelisable.*

- [ ] **6.1** Implement `drivers/soundboard/driver.js` pairing flow per SPEC §8.1: custom pairing view with IP/port input, connect attempt, `hello`/`hello_ack` handshake, error + retry on timeout. Playwright tests: happy path, timeout error shown, retry works.
- [ ] **6.2** Implement Android Settings screen per SPEC §5.5: device name field, port field in Advanced section, developer tips expandable section with step-by-step instructions. Espresso tests: name saves to shared prefs, port change restarts server on save, developer tips section expands/collapses.
- [ ] **6.3** Implement all Homey Flow trigger, condition, and action cards per SPEC §8.4. Tests: `play_sound` action sends correct message and sets handle tag, `stop_sound` sends stop with handle, `stop_all` sends stop_all, `sound_is_playing` condition checks handle store, `set_volume` sends correct volume mapping.

---

## Group 7 — Polish and Validation
*Depends on Group 6.*

- [ ] **7.1** Full integration smoke test (Homey side): `MockWebSocketClient` simulates complete session — connect, library sync, play, started callback, natural done, loop play, stop command, done-on-disconnect for remaining handles. Assert final device state and all Flow triggers fired in correct order.
- [ ] **7.2** Full integration smoke test (Android side): Ktor test engine simulates Homey client — connect, play 3 sounds simultaneously, stop one, wait for others to complete, verify all `done` messages received with correct reasons and handles.
- [ ] **7.3** Verify Homey coverage ≥ 65% and Android coverage ≥ 65%. Fill gaps with targeted tests. Run `homey app validate`. Write README covering: pairing instructions, how to import sounds, how to use Flow cards, how to run tests.
