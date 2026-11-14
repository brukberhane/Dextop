# T01 — XR pose provider abstraction + skeleton

**Status**: Done  
**Parent INDEX**: [INDEX.md](./INDEX.md)  
**Depends-on**: —  
**Next**: T02  
**Layer**: L0

## Description

Land the compile-clean skeleton of the XR glasses feature: a Kotlin-side
`XrPoseProvider` interface (3DoF orientation stream + provider identity +
recenter hook), a deterministic fake provider for tests, and the seam where
XR mode will plug into the existing external-display pipeline. No hardware
protocols, no UI. After this task `make verify` passes with the new code in
the tree, and every later XR task has a stable interface to target.

## Status History

| Timestamp | Event | From | To | Details | User |
| --------- | ----- | ---- | -- | ------- | ---- |
| 2026-11-14 | planned by /task-1-plan | Pending | Planned | execution plan written; ktest gate-wiring added to scope after reality check (verify didn't run Kotlin tests; CI does) | |
| 2026-11-14 | /task-2-execute started | Planned | InProgress | baseline ktest before wiring into verify | |
| 2026-11-14 | /task-3-complete | InProgress | Done | re-verify green; dialectic encoded; INDEX ✅ | |

## Requirements

- [ ] `XrPoseProvider` interface: stream of 3DoF orientation (yaw/pitch/roll or quaternion, timestamped), provider id/name, recenter callback, availability state
- [ ] Fake provider: scriptable pose sequence, injectable for tests
- [ ] Provider registry / lookup seam in the Kotlin service layer (empty except fake)
- [ ] Kotlin code follows existing package layout (`moe.n4tsu.dextop`)
- [ ] Unit tests for fake provider stream semantics (subscription, timestamps, recenter reset)

## Implementation Plan

*(Filled by `/task-1-plan` — do not invent during bootstrap beyond high-level notes.)*

### High-level notes (bootstrap)

- Interface lives Kotlin-side: pose consumers (mirror transform, T05) are all
  in the service layer. Flutter never sees raw pose.
- Keep the interface transport-agnostic: Viture HID (T03) and XREAL TCP (T04)
  both implement it; generic fallback implements nothing (absent provider).
- Recenter is part of the interface because Viture has a native HID recenter
  command (`0x30`) while XREAL recenter is host-side yaw reset — consumers
  must not care which.
- Reference: `.cursor/rules/xr-glasses.mdc` (settled decisions),
  `.cursor/rules/display-stack.mdc` (pipeline invariants).
- Prior art: `one-xr` pose/calibration model (https://github.com/Skarian/one-xr).

## Execution plan (filled by /task-1-plan)

**Date:** 2026-11-14  
**Codebase snapshot:** main @ fa60560 (bootstrap commit); verify gate = `make verify` (analyze + flutter test + flavored APK build); lefthook pre-commit wired.  
**Execute model:** [medium] (default) | large not justified — design fully settled below, execution is mechanical

### Context for executor

**Goal:** Add the Kotlin-side XR pose abstraction that every later XR task codes
against: `XrPoseProvider` interface (3DoF orientation stream + availability +
recenter), a deterministic fake provider, a thread-safe registry seam, and
Kotlin unit tests. No hardware, no Flutter surface, no production wiring.

**Key files/packages (all new unless noted):**

- `android/app/src/main/kotlin/moe/n4tsu/dextop/xr/XrPoseProvider.kt` — `XrPose` data class, `XrAvailability` enum, `XrPoseListener` fun interface, `internal interface XrPoseProvider`, `internal abstract class AbstractXrPoseProvider` (origin-offset math + listener fan-out)
- `android/app/src/main/kotlin/moe/n4tsu/dextop/xr/FakeXrPoseProvider.kt` — test fake, `id = "fake"`, scriptable via public `push(...)`
- `android/app/src/main/kotlin/moe/n4tsu/dextop/xr/XrPoseProviders.kt` — `internal object` registry (`register`/`unregister`/`all()`/`byId`)
- `android/app/src/test/kotlin/moe/n4tsu/dextop/xr/FakeXrPoseProviderTest.kt`
- `android/app/src/test/kotlin/moe/n4tsu/dextop/xr/XrPoseProvidersTest.kt`
- `Makefile` (edit) — new `ktest` target, wired into `verify`
- `.cursor/rules/general.mdc` (edit, one line) — sync the verify description (explicit rules-file exception: this plan's step 4, user-approved by approving this plan)

**Style invariants (from repo reality, match them exactly):**

- Kotlin sources use **callbacks/listeners + `Handler(Looper.getMainLooper())` / single-thread executors**, NOT kotlinx.coroutines Flow. Do not introduce `SharedFlow`/`StateFlow`. Listener fan-out may use `java.util.concurrent` (`ConcurrentHashMap`, `synchronized`) per `MirrorService.kt:101-103` precedent.
- Mark the subsystem `internal` (display-stack precedent: `internal interface` in `DisplayMirrorBackend.kt:17-47`). Unit tests can see `internal` from the test source set.
- Tests use **`kotlin.test`** (`kotlin.test.Test`, `assertEquals`) — majority style (`DeviceMatchTest.kt:3-6`). No new test dependencies.
- Logging: `android.util.Log` with tag `"DextopXr"` if any logging is needed at all (keep T01 near-silent).

**API shape (settle exactly; do not redesign):**

```kotlin
package moe.n4tsu.dextop.xr

data class XrPose(
    val timestampNanos: Long,   // provider clock: SystemClock.elapsedRealtimeNanos()
    val yawRadians: Float,      // origin-relative, positive = head turns LEFT (CCW from above)
    val pitchRadians: Float,    // origin-relative, positive = head tilts UP
    val rollRadians: Float,     // positive = head rolls RIGHT (clockwise for wearer)
)

enum class XrAvailability { UNAVAILABLE, CONNECTING, STREAMING, ERROR }

fun interface XrPoseListener { fun onPose(pose: XrPose) }
fun interface XrAvailabilityListener { fun onAvailabilityChanged(availability: XrAvailability) }

internal interface XrPoseProvider {
    val id: String                  // stable, e.g. "fake", "viture-pro", "xreal-one"
    val displayName: String
    val availability: XrAvailability
    fun addPoseListener(listener: XrPoseListener)
    fun removePoseListener(listener: XrPoseListener)
    fun addAvailabilityListener(listener: XrAvailabilityListener)
    fun recenter()
}

internal abstract class AbstractXrPoseProvider : XrPoseProvider {
    // Subclasses call protected publishRaw(rawYaw, rawPitch, rawRoll, timestampNanos).
    // Base applies origin offsets (pose = raw - origin per axis), fans out to
    // pose listeners under one lock, tracks lastRaw.
    // recenter(): synchronized { origin = lastRaw }  -> subsequent poses ≈ 0.
    protected fun publishAvailability(next: XrAvailability)  // fans out availability listeners
}

internal class FakeXrPoseProvider : AbstractXrPoseProvider() {
    // id "fake"; public fun push(rawYaw: Float, rawPitch: Float, rawRoll: Float, timestampNanos: Long)
    // public fun setAvailability(...) for tests
}
```

**Contract rules (document in KDoc on the interface — executor copies these):**

1. Poses are **origin-relative**: recentering shifts future poses so the current
   head direction reads ≈ 0. Consumers never do origin math.
2. Values are **unwrapped and continuous** — providers must NOT wrap yaw/pitch
   to [-π, π]; viewport math (T05) depends on continuity.
3. Vendor frames (Viture NWU Euler, XREAL sensor fusion) are converted to this
   contract inside the adapters (T03/T04), never by consumers.
4. Listener callbacks fire from a provider-owned thread; consumers must not
   block. Ordering: poses arrive in nondecreasing `timestampNanos` order.
5. `recenter()` is synchronous and idempotent; safe before first pose (no-op).

### Steps

1. **Baseline check first.** Add `ktest` target to `Makefile`:

   ```make
   ktest:
   	cd android && ./gradlew :app:testGithubDebugUnitTest
   ```

   (exact CI command shape: `.github/workflows/debug-apk.yml:57` runs
   `./gradlew :app:testGithubDebugUnitTest` from `android/`). Run
   `make ktest` on the **unmodified** tree.
   → verify: existing 78 Kotlin tests pass.
   → **decision gate:** if pre-existing tests fail, do NOT wire `ktest` into
   `verify` (dead gate) — still add the target, record baseline failures in
   Verification, set task Status `Blocked` per skill, and tell the user.
   If green, change `verify: analyze test build` → `verify: analyze test ktest build`.
   Also add `ktest` to the `help` target text.

2. **Create `xr` package** with `XrPoseProvider.kt` (types + interface +
   `AbstractXrPoseProvider`), `FakeXrPoseProvider.kt`, `XrPoseProviders.kt`,
   exactly per the API shape above. Registry: in-memory map keyed by `id`,
   `synchronized` access; `register` replaces same-id; `byId` returns null when
   absent; **no production registration of anything** (fake is registered only
   by tests).
   → verify: `make build` compiles the new sources into the APK.

3. **Tests.** `FakeXrPoseProviderTest.kt` + `XrPoseProvidersTest.kt`, kotlin.test style:

   | Test case | Asserts |
   | --------- | ------- |
   | Push → listener receives pose | values + timestamp pass through |
   | Two listeners both notified | fan-out |
   | removePoseListener stops delivery | no callback after remove |
   | Recenter before any pose | later poses unaffected by recenter (origin stays initial) |
   | push(yaw=0.3) → recenter → push(yaw=0.3) | second pose yaw ≈ 0 (within 1e-4) |
   | Origin offset applied per-axis | pitch and roll also reset |
   | Availability transition | UNAVAILABLE → STREAMING notifies availability listener |
   | Registry register/byId/unregister | idempotent replace, null after unregister |
   | Empty registry | `all()` empty at init (fresh object per test — use a resettable test hook or construct around it) |

   → verify: `make ktest` green with new tests visible in output.

4. **Hub sync (rules-file edit — user pre-approved via this plan):** in
   `.cursor/rules/general.mdc`, update the Build/verify description from
   "`make verify` = `flutter analyze` + `flutter test` + `flutter build apk
   --debug`" to include "Kotlin unit tests (`make ktest`, gradle
   `:app:testGithubDebugUnitTest`)" in the chain. One sentence change, nothing
   else.

5. **Full gate.**
   → verify: `make verify` green end-to-end (analyze + flutter test + ktest + build).

### Tests to add

See step 3 table — 9 cases across two files. No Flutter/Dart tests in this task
(no Dart code touched).

### Verify commands

- `make ktest` (new; Kotlin unit tests, github flavor debug variant)
- `make verify` (full gate; must pass before AC)

### Risks / pitfalls

- **Pre-existing Kotlin test failures** would block wiring `ktest` into
  `verify` — handled by step 1's decision gate; do not "fix" unrelated tests
  (Surgical Changes).
- **Flavor task name**: unit test task is `testGithubDebugUnitTest` because of
  the `distribution` flavor dimension — `:app:test` alone does not exist;
  `testDebugUnitTest` is ambiguous with flavors. Use the exact task from CI.
- **Gradle daemon cost**: `ktest` adds one Gradle invocation per commit; warm
  daemon keeps it short, but first run may download/configure. If verify time
  becomes painful, that is a hub concern for later — not something to
  "optimize" inside this task.
- **Do not** add kotlinx-coroutines-flow because it looks modern — repo has
  zero Flow usage; callbacks are the established idiom.
- **Do not** wrap angles — continuity is a hard contract (T05 depends on it).
- `internal` visibility: keep it; test source set can access it. If the build
  rejects `internal` from tests (it should not — same module), fall back to
  `public` with a KDoc note, do not restructure.

### Out of scope

- Any Viture/XREAL protocol code (T03/T04), detection/UI (T02), fusion/viewport
  (T05), Flutter/Dart changes, MethodChannels, production registration of
  providers, smoothing/damping math.

### Execute model recommendation

- [medium] (default) — Gradle/Makefile wrangling plus a contract-setting
  interface benefit from a mid-tier model; [small] is acceptable if the plan
  is followed cold since all design decisions are settled above.

## Test Plan

- Unit tests over the fake provider (no hardware): subscription lifecycle, timestamp monotonicity, recenter reset semantics
- Commands: `make verify` (must include analyze + tests)

## Acceptance Criteria

- [x] `XrPoseProvider` interface + fake provider exist Kotlin-side, transport-agnostic
- [x] Provider registry seam resolves fake in tests, nothing in production yet
- [x] Tests added for fake provider semantics
- [x] Full analyze + test verify suite green (`make verify`)
- [x] Verification commands recorded and passing
- [x] No secrets committed

## Verification

**Date:** 2026-11-14 (Africa/Addis_Ababa)

Baseline `make ktest` on the unmodified Kotlin tree: `BUILD SUCCESSFUL` (58s). Pre-existing suite green, so `ktest` was wired into `verify`.

`make verify` (presence check: Makefile `verify`, `lefthook.yml`, `analysis_options.yaml` all present):

- analyze: `No issues found` (5.9s)
- flutter test: `00:11 +69: All tests passed!`
- ktest: `:app:testGithubDebugUnitTest` `BUILD SUCCESSFUL` (2s). New tests: `FakeXrPoseProviderTest` 7/7, `XrPoseProvidersTest` 3/3, 0 failures (`build/app/test-results/testGithubDebugUnitTest/`)
- build: `Built build/app/outputs/flutter-apk/app-github-debug.apk`

Deviation from the plan's `override var availability` / `protected set`: a protected setter emits JVM `setAvailability`, which collided with `FakeXrPoseProvider.setAvailability` (`Accidental override`). Availability is now a `val` over a `@Volatile` private field; `publishAvailability` writes it. Behavior unchanged.

Nothing registered in production. No secrets.

**Close-out re-verify (2026-11-14):** `make verify` again — analyze clean (5.7s), flutter test 69 passed, ktest BUILD SUCCESSFUL (9s), `app-github-debug.apk` built. exit 0.

## Files Modified

- `android/app/src/main/kotlin/moe/n4tsu/dextop/xr/XrPoseProvider.kt` (new)
- `android/app/src/main/kotlin/moe/n4tsu/dextop/xr/FakeXrPoseProvider.kt` (new)
- `android/app/src/main/kotlin/moe/n4tsu/dextop/xr/XrPoseProviders.kt` (new)
- `android/app/src/test/kotlin/moe/n4tsu/dextop/xr/FakeXrPoseProviderTest.kt` (new)
- `android/app/src/test/kotlin/moe/n4tsu/dextop/xr/XrPoseProvidersTest.kt` (new)
- `Makefile` (`ktest` target; `verify` = analyze + test + ktest + build)
- `.cursor/rules/general.mdc` (verify description includes ktest)
- `planning/phases/T01-xr-pose-provider-skeleton.md`, `planning/phases/INDEX.md` (status InProgress)

## Manual test (for humans)

Nothing to test — T01 is a Kotlin unit-testable skeleton with no production
registration, UI, or hardware path. Confirm with:

```sh
make ktest
```

Expect `FakeXrPoseProviderTest` and `XrPoseProvidersTest` green under
`:app:testGithubDebugUnitTest`.

## Learnings

- Dialectic Mode A: Kotlin `var` custom/`protected set` and a same-named method
  collide on the JVM as `setX` — encoded in
  `.cursor/rules/flutter-dart.mdc` (`Kotlin property setter / method JVM clash`).
- Mode B: `moe.n4tsu.dextop.xr` package + registry layout noted in
  `.cursor/rules/xr-glasses.mdc`; `make ktest` documented in hub + README +
  flutter-dart tests invariant.
- T02 Reality notes updated for provider seam + ktest gate.

## Reality notes

*(Amended by upstream `/task-3-complete` if prior tasks changed assumptions)*
