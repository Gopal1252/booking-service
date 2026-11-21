# Conflict-Safe Appointment Booking

Submission for the **AI-Assisted Engineering Assessment, Appointment B**. It provides conflict-safe booking and cancellation as a small Java service with a CLI and an in-memory store.

## Requirements

**To install:**
- **JDK 21**
- **Maven 3.9+**

Tested with OpenJDK 21.0.9 and Maven 3.9.14.

**Fetched automatically by Maven** on the first build (internet needed once), declared in `pom.xml`:

| Dependency / plugin | Version | Used for |
|---|---|---|
| JUnit Jupiter (`junit-jupiter`) | 5.11.4 | tests (test scope only) |
| `maven-compiler-plugin` | 3.13.0 | compiling for Java 21 |
| `maven-surefire-plugin` | 3.5.2 | running the tests (`mvn test`) |
| `exec-maven-plugin` | 3.5.0 | running the CLI (`mvn exec:java`) |

The application has no runtime dependencies beyond the JDK: no database, no network services.

## Setup and run

```bash
git clone <repo-url> && cd booking-service

mvn test                     # run all tests (26 tests)
mvn -q compile exec:java     # start the interactive CLI
```

In IntelliJ, open the folder as a Maven project. Run tests from `src/test/java`, or run `booking.Main` to start the CLI.

**CLI commands**

| Command | What it does |
|---|---|
| `book <providerId> <start> <end>` | Book `[start, end)`, e.g. `book dr-smith 2026-11-20T10:00:00Z 2026-11-20T11:00:00Z` |
| `cancel <appointmentId>` | Cancel an appointment, freeing its slot (use the full id printed by `book`) |
| `list <providerId>` | Show a provider's appointments, active and cancelled |
| `help` / `exit` | Show commands / quit (Ctrl-D also quits) |

```
> book dr-smith 2026-11-20T10:00:00Z 2026-11-20T11:00:00Z
BOOKED  id=011e6cf0-…  dr-smith  2026-11-20T10:00:00Z to 2026-11-20T11:00:00Z
> book dr-smith 2026-11-20T10:30:00Z 2026-11-20T11:30:00Z
REJECTED  CONFLICT: overlaps appointment 011e6cf0-…
> cancel 011e6cf0-…
CANCELLED
```

Full sessions are in `evidence/` (see below).

## Assumptions and the booking invariant

**Invariant:** for any provider, no two **ACTIVE** appointments overlap.
- Intervals are half-open, `[start, end)`. Two overlap when `a.start < b.end && b.start < a.end`.
- Adjacent appointments are allowed (one ends at 11:00, the next starts at 11:00).
- Cancelled appointments don't block time, and different providers never conflict.
- Enforced only in `BookingService`.

**Assumptions:**
- All timestamps are **UTC**, written as ISO-8601 with a `Z` suffix. Offsets like `+05:30` are rejected.
- A booking is start + end, and duration must be > 0. Past bookings are allowed. There is no maximum duration and there are no business hours.
- Appointment ids are generated UUIDs. Provider ids are any non-blank string. Cancelled appointments are kept with status `CANCELLED`.
- Invalid input (bad or missing timestamps, non-positive duration, missing ids) is rejected without changing stored bookings.

Details and reasoning are in `DESIGN.md`.

## Limitations and follow-up risks

- **In memory only.** Data is lost on exit and isn't shared between processes. **Follow-up risk:** the lock only works inside one JVM. Moving to a real database would need the invariant enforced there instead, e.g. with a transaction plus a conflict check, or an exclusion constraint.
- **One global lock.** Bookings for different providers wait for each other, though they can never conflict. That costs throughput, not correctness. The fix would be per-provider locks, which need a thread-safe store.
- **Linear overlap check.** It scans all of a provider's appointments, O(n). Cancelled appointments are never cleaned up, so the scan and memory grow over time.
- **No business limits:** no maximum duration, no business hours, no limit on booking in the past or far ahead.
- **CLI quirks:**
  - Arguments are split on whitespace, so a blank argument becomes a usage error before reaching the service. The service still rejects blank input for any other caller, and tests cover that.
  - `list` shows booking order, not start-time order.
- **Provider ids are compared exactly.** `dr-smith`, `Dr-Smith` and ` dr-smith` are different providers.

## How it was verified

| Test class | Covers |
|---|---|
| `BookingServiceTest` (19) | Successful booking (AC1), same-provider overlap and different providers (AC2), adjacency (AC3), cancel-then-rebook, missing and already-cancelled (AC4), 9 invalid-input cases plus blank-id cancel, each checking the store is unchanged (AC5) |
| `BookingServiceConcurrencyTest` (2) | 50 threads × 200 rounds: exactly one overlapping booking wins; exactly one concurrent cancel wins |
| `InMemoryAppointmentStoreTest` (5) | Store mechanics: save, find, replace on cancel without duplicating the index |

- **Mutation checks.** Each rule was broken on purpose in a throwaway copy, and exactly one test failed each time:
  - removing the `Z` check
  - making the overlap check `<=`
  - ignoring status in the overlap check
  - removing the already-cancelled check
- **Full suite.** I ran `mvn test` myself on the final code: 26/26 pass.
- **Concurrency.** With the lock removed, both concurrency tests fail. I reproduced this myself in the real code.
- **Manual demo.** I ran the CLI by hand, alongside a scripted demo.

### Evidence folder

`evidence/` holds the saved output behind the claims above:

| File | What it shows | How it was produced |
|---|---|---|
| `all-tests-pass.txt` | The full suite passing on the final code: 26 tests, 0 failures | Me: `mvn test` on the final code |
| `cli-demo-scripted.txt` | A full CLI session covering every command and result: book, conflict, adjacency, other provider, invalid input, cancel, rebook, already-cancelled, not-found, list, usage errors | Claude Code drove the CLI with a script (P4) |
| `cli-demo-manual.txt` | My own CLI session, typed by hand | Me, in the terminal (P4) |
| `concurrency-tests-fail-without-lock.txt` | Both concurrency tests failing once the lock is removed: 50 of 50 overlapping bookings succeed, and 3 threads cancel the same appointment. This proves the tests really detect the race. | Me: I commented out the lock in the committed code and ran the tests (P5) |

## How AI was used

- **Design:** discussed on claude.ai, giving the spec in `docs/build-spec.md`.
- **Build:** done with Claude Code in IntelliJ, one phase at a time (P0 to P6), with manual approval of each change. I reviewed the code and tests after every phase, and committed between phases.
- **Accepted, edited or rejected:** every suggestion is marked in `AI_PROMPT_LOG.md`, with verification evidence. Examples of my own review:
  - **Record immutability (design, Entries 4–5).** The AI proposed cancelling by "flipping the status" of an `Appointment` record. I pointed out that record fields are final, so status can't be changed in place. The design was corrected: `cancel()` returns a new record with status `CANCELLED`, and the store replaces the old one.
  - **Missing provider index (design, Entry 4).** I noticed the store had only an id map and no provider lookup. `byProvider` was added.
  - **Test scope (design, Entries 2–3).** I cut the proposed 16 tests to one per acceptance criterion (edited).
  - **Concurrency (build, Entry 26).** I rejected a code change the AI started before the decisions were final.
  - **Stretch tests (Entry 29).** I dropped them as unnecessary.
  - **Verified myself:** the no-lock failure and the CLI demo (see `evidence/`).

## Repository layout

```
README.md              this file
DESIGN.md              short design spec
AI_PROMPT_LOG.md       AI prompts, decisions, verification
docs/
  build-spec.md        full working spec (archived)
  assessment.pdf       problem statement
evidence/              saved verification output (see "Evidence folder")
src/main/java/booking/ service, store, model, CLI
src/test/java/booking/ unit and concurrency tests
```
