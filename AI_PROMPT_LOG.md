# AI Prompt and Use Record

Project: Appointment Booking, conflict-safe booking and cancellation
Tools: claude.ai (design phase), Claude Code in IntelliJ (build phase)

Legend: **Accepted** = used as given, **Edited** = used with changes, **Rejected** = not used.

**Note on file references:** until Entry 30, the working spec was `DESIGN.md` at the repo root. In Entry 30 it moved to **`docs/build-spec.md`**, and a new short `DESIGN.md` was written for reviewers. So "DESIGN §N" in Entries 1 to 29 means a section of `docs/build-spec.md`. Similarly, the evidence files named in earlier entries were moved to `evidence/` (Entry 29) and renamed (Entry 31):
- `demo-transcript.txt` → `evidence/cli-demo-scripted.txt`
- `demo_transcript_manual.txt` → `evidence/cli-demo-manual.txt`
- `concurrency-no-lock-failure.txt` → `evidence/concurrency-tests-fail-without-lock.txt`

---

## Phase 1: Design (claude.ai)

### Entry 1: Problem context, requirements, deliverables

**Prompt:**
> I have to solve an AI Proficiency Engineering assessment wherein I need to make scheduling feature with the use of AI and keep it bounded to the suggested requirements. I have attached the assessment PDF along.
> Firstly, provide me a context of the problem statement and then list the requirements and the deliverables as lists.
> Note that there is no starter project in here, and it seems it has been mentioned in the document, how to handle that case.
>
> I plan to discuss the design and plan how to actually develop the feature with claude.ai here, but the actual code writing and development would be done with the help of claude code in intellij.
>
> Also, note that I will need the prompt and responses here to go into a file later as i think it is also a deliverable
>
> So, first give me the context of the problem statement and the requirements and deliverables list.

**Response summary:**
- Context: build only the booking and cancellation slice. Core invariant: no two active appointments for the same provider overlap, using half-open intervals `[start, end)`.
- No starter project, so the PDF's fallback applies: a small service or CLI prototype with an in-memory store.
- Requirements listed by category: functional (7 items), constraints (UTC, scope limits), testing (6 test categories, concurrency test optional but recommended), AI usage record.
- Deliverables listed: code and tests, design spec, AI prompt record, setup/run instructions, assumptions and invariant note, limitations and risks.
- Suggested keeping a running prompt log, and asked which stack to use next.

**Decision:** Accepted

**Informed:** Scope of the project, requirement checklist, deliverables checklist.

**Verification:** Checked the requirements and deliverables lists against the assessment PDF myself. Nothing was missing or added.

---

### Entry 2: Tech stack selection and prompt log setup

**Prompt:**
> Okay, I accept this response (though I probably won't be rejecting the responses in the design phase)
>
> Can you generate a markdown file with appropriate name to append the
> 1) prompt, 2) a summary of response, 3) [accepted/edited/rejected] flags.
> Make an entry for previous prompt response and after every response in the future?
> Since this is design phase, select accepted for the current responses, once we shift to the build phase in claude code, i can add the verification proof/reasoning there.
>
> As for the response - requirements seem correct and none seem to be missing, same with the deliverables
>
> My suggestions:
> 1) Use plain Java - since the feature is bounded and simple and Spring Boot would require unnecessary.
> 2) Tests - JUnit tests are fine. Also provide a list of the tests that have been mentioned in the docs, i believe there are not many.
> 3) We can use CLI for using the feature we build. Let's keep it simple
> 4) I think I would like to use Intellij as the IDE and maven to build the project - just keeping it simple
>
> these are my tech stack selection, can you tell me how do you like them?
> After this we can move to the actual designing.

**Response summary:**
- Created this log file (`AI_PROMPT_LOG.md`).
- Agreed with the stack: plain Java, JUnit 5, CLI, Maven, IntelliJ. Suggested Java 21 (or 17) for records and `java.time`.
- Raised one issue: with an in-memory store, a one-command-per-run CLI loses state between runs. The CLI should be an interactive loop (REPL) so state lives for the session.
- Suggested keeping the CLI as a thin layer over a service class, so tests target the service directly.
- Listed the tests implied by the PDF, grouped by category, plus an optional concurrency test.

**Decision:** Edited

**Edits made (see Entry 3):**
- Test list cut from 16 detailed cases to one core test per PDF category. The extra edge cases and the concurrency test become stretch goals, done only if time allows.
- Accepted the CLI REPL idea, but deferred the class names and layering (`Main` / `BookingService` / `AppointmentStore`) to the design discussion instead of fixing them now.

**Informed:** Tech stack, CLI shape (REPL), test scope.

**Verification:** Checked the test list against acceptance criteria 1 to 6 in the PDF.

---

### Entry 3: Trim test scope, defer class design

**Prompt:**
> just one edit (update edited in the logs file as well)
> 1) the number of tests you have listed is way too much. as per dock there are 5 major ones: successful booking, same-provider overlap, adjacency, different providers,
> cancellation, and invalid input. so let's first majorly check the basic categories, we can surely go for all the tests if we get time.
> 2) Got the CLI REPL loop. But I haven't decided the names or the exact design of the classes, we will do that next.
> sounds good?

**Response summary:**
- Agreed. Marked Entry 2 as Edited.
- Noted that the PDF actually lists 6 categories (the user's list also has 6 items).
- Proposed a core test set: one test per category, with cancellation getting a second test because criterion 4 has two parts (freeing the slot, and a clear result for missing or already-cancelled bookings).
- Extra edge cases and the concurrency test are now stretch goals.
- Left the class design for the next step.

**Decision:** Accepted

**Informed:** Core test plan.

**Verification:** Checked the core set against acceptance criteria 1 to 6 in the PDF.

---

### Entry 4: Proposed design

**Prompt:**
> yes, typo on the count.
> next, design
> The problem statement as explained by you is simple.
> Can you suggest the design we should be following here? I will review it and ask any clarifying questions if needed

**Response summary:**
- Layers: `Main` (CLI REPL) -> `BookingService` (validation, locking, overlap check) -> `InMemoryAppointmentStore` (map by id).
- Model: `Appointment` record (id, providerId, start, end, status), `Status` enum ACTIVE/CANCELLED. Cancel keeps the record and flips status instead of deleting it.
- API: `book(providerId, start, end)` takes raw strings so the service owns all validation and is testable. Returns sealed `BookingResult` (Booked / Rejected with reason). `cancel(id)` returns `CancelResult` enum (CANCELLED / NOT_FOUND / ALREADY_CANCELLED). No exceptions for business outcomes.
- Invariant: for one provider, no two ACTIVE appointments satisfy `a.start < b.end && b.start < a.end` (half-open `[start, end)`, so adjacency is allowed).
- Validation runs fully before any store write, so rejected input never changes state.
- Concurrency: one lock around check-then-insert in `book` and around `cancel`. Per-provider locks offered as an optional upgrade.
- Assumptions: UTC ISO-8601 with `Z`, past bookings allowed, no max duration, server-generated UUID ids.
- Open decisions for the user: start+end vs start+duration input, strict `Z` vs accepting offsets, lock granularity.

**Decision:** Edited

**Edits made (see Entry 5):**
- Asked for an explicit class list, since the response described layers but not every class.
- Found a gap: the store had only `Map<id, Appointment>`, with no provider-to-appointments lookup.
- Questioned "flip the status" on a record, since record fields are final. The cancel mechanism was clarified to replace the record.
- Concurrency (locking) deferred: build single-threaded first, add locking after.
- Three open decisions taken as recommended: start + end input, strict `Z` UTC timestamps, single lock when concurrency is added.

---

### Entry 5: Class list, provider index, record immutability, defer concurrency

**Prompt:**
> some points:
> 1) you didn't give me the exact list of entities/classes i wanted
> from what i understand these are the classes:
>
> * BookingService (orchestrate from here)
> * InMemoryAppointmentStore
> * Appointment
> * BookingResult (a sealed interface)
> * Status (enum)
> * CancelResult (enum)
> * Main for demo/REPL
>
> 2) InMemoryAppointmentStore stores the Map<id,Appointment> but where do i store another Map to map out the providerId to the datastructure containing hte Appointments
> 3) You said that Appointment is a record, and we can cancel an appointment by setting the enum sttus to cancelled, but isn't the enum immutable in the record?
> 4) Regarding the concurrency - let's first build for single threaded environment, after that we take care of the concurrency
> Make sure to update the AI logs

**Response summary:**
- Confirmed the user's class list and added the missing pieces: `Booked` and `Rejected` records (the two `BookingResult` cases), a `Reason` enum, and a `BookingServiceTest` test class.
- Provider index: the store holds both maps. `byId: Map<String, Appointment>` is the single source of truth. `byProvider: Map<String, List<String>>` holds appointment ids only, so a replaced record never goes stale in the index.
- Record immutability: the user was right, record fields are final. Cancel builds a new record with status CANCELLED (`appointment.cancel()`) and saves it under the same id, replacing the old one. The earlier "flip the status" wording was imprecise.
- Concurrency deferred. Kept the check-and-save inside one service method so a lock can wrap it later without a redesign.

**Decision:** Accepted

**Informed:** Final class list, store data structure, cancel mechanism, build order (single-threaded first).

**Verification:** Checked the record point against Java semantics (record components are implicitly `private final`, with no setters).

---

### Entry 6: Should cancel remove the id from byProvider?

**Prompt:**
> if an appointment is cancelled, do we not remove it from the byProvider map?

**Response summary:**
- Both approaches are valid. Recommended keeping the id in `byProvider` and filtering by `status == ACTIVE` during the overlap check.
- Reasons: one source of truth for "is it active" (the status field), cancel stays a single write (replace in `byId`), and `list` can still show cancelled appointments.
- Removing the id is a valid optimization (a smaller scan), but it means two writes per cancel and two places that encode "active", which can drift apart. If chosen, the map should be renamed `activeByProvider`.
- `ALREADY_CANCELLED` detection works either way, because it reads `byId`.

**Decision:** Accepted (the principle). Accepted "one source of truth" as the guiding rule. The final choice of keeping vs removing the id from `byProvider` is deferred to the build phase.

---

### Entry 7: Class relationships

**Prompt:**
> okay, got it, having one source of truth is better (need to remember this). I am not sure about the removing it from the byProvider map or not , i can probably discuss it during the actual building.
> do remember to update the ai logs.
>
> um, i think we are done with most of the things, isn't it. um, can you give me the relationships also between the entities/classes identified and i think after that i will have you build the design doc to give to claude code and then i can continue there.

**Response summary:**
- Gave a class relationship diagram plus a relationship table.
- `Main` uses `BookingService`. `BookingService` owns an `InMemoryAppointmentStore` (passed in through the constructor so tests can create fresh ones).
- The store holds many `Appointment`s, and each `Appointment` has one `Status`.
- `BookingService` creates `Appointment`s and returns `BookingResult` / `CancelResult`. `Booked` and `Rejected` implement `BookingResult`; `Booked` wraps an `Appointment`, and `Rejected` has a `Reason`.
- Dependency rule: dependencies point downward only. The store knows nothing about results or validation, and the model knows nothing about the service.
- Confirmed design is essentially complete. Next step is the design doc for Claude Code.

**Decision:** Accepted

**Informed:** Class relationships and dependency direction for the design doc.

---

### Entry 8: Confirm public service API

**Prompt:**
> and we are just exposing two main service APIs - > book and cancel  and just one more to list the appointments by the providerId for debugging purposes, am i right? respond in short

**Response summary:**
- Confirmed: `book(providerId, start, end)` and `cancel(appointmentId)` are the feature API. `listForProvider(providerId)` is a read-only helper for the REPL and debugging, outside the required scope.

**Decision:** Accepted

**Informed:** Public API surface of `BookingService`.

---

### Entry 9: Design doc

**Prompt:**
> i don't see entry 8.
> Okay, good with the confirmation. Can you give me a design doc - markdown preferrable to give to claude code, but for my own reference also, so keep it nice and referrable/readable. I also want you to provide the context and purpose of this feature i.e. for the ai assessment.

**Response summary:**
- Entry 8 was written but the log file wasn't resent. Resent it.
- Created `DESIGN.md`, which covers: context and purpose (the assessment), scope, acceptance criteria, booking invariant, assumptions, architecture, classes and relationships, store data design, service API and flows, CLI, concurrency plan (Phase 2), test plan (core + stretch), project layout, build order, instructions for Claude Code, and known limitations.
- Two additions not discussed before:
  1. Added `INVALID_INPUT` to `CancelResult`, because AC5 requires rejecting missing identifiers and cancel had no way to say so.
  2. Flagged that `Instant.parse` accepts offsets since JDK 12, so strict UTC needs an explicit `Z` check, with a test to verify it.

**Decision:** Accepted

**Informed:** `DESIGN.md` (handed to Claude Code for the build). The user reviewed it and accepted it as written, including both additions.

---

### Entry 10: Split the build into phases

**Prompt:**
> okay. one more thing. instead of building in one go, i was thinking of dividing the feature development into a few phases. can you suggest some? don't update the design doc with the phases untill i tell you to

**Response summary:**
- Proposed 7 phases, each with a goal, the work involved, and a "done when" gate:
  - P0 project skeleton
  - P1 domain model + store
  - P2 booking + validation (T1 to T4, T7)
  - P3 cancellation (T5, T6)
  - P4 CLI REPL + a saved demo transcript
  - P5 concurrency (lock + concurrency test, plus proving the test fails without the lock)
  - P6 stretch tests + docs (README, limitations, final log)
- Suggested one Claude Code session, one log entry and one git commit per phase, so each phase leaves its own verification evidence.
- `DESIGN.md` not changed, as instructed.

**Decision:** Edited

**Edits made (see Entry 11):**
- P0 (the skeleton) is done by the user by hand in IntelliJ, not by Claude Code.
- Dropped the "one git commit per phase" rule. The user commits at whatever granularity he chooses.

---

### Entry 11: Adjust phases for manual project init and own commits

**Prompt:**
> okay. i think i will initialize the project in intellij myself, so adjust the plan accordingly. I also plan to make step by step any git commits i like

**Response summary:**
- P0 becomes a manual step: create a Maven project in IntelliJ (JDK 21), add the `junit-jupiter` dependency and a Surefire 3.x plugin, and confirm `mvn test` runs. The `exec-maven-plugin` (for the REPL) can wait until P4.
- Claude Code starts at P1. Phases P1 to P6 are unchanged apart from that.
- Git: the user commits at his own granularity. Only suggestion: commit before each Claude Code session, so a diff shows exactly what the AI changed.

**Decision:** Accepted

**Informed:** Final phase plan (not yet written into DESIGN.md).

---

### Entry 12: Restate final phases

**Prompt:**
> give me the finalised phases once more?

**Response summary:** Restated the final plan P0 to P6. P0 is manual in IntelliJ; Claude Code handles P1 to P6. Each phase has a "done when" gate.

**Decision:** Accepted

---

### Entry 13: Add phases to the design doc

**Prompt:**
> okay, add these to the design doc, and then i will continue with claude code

**Response summary:** Replaced Section 15 of `DESIGN.md` (Build Order) with "Build Phases", a P0 to P6 table giving owner, work and "done when" for each phase, plus the per-phase rules (commit before each session, one log entry per phase). Updated the Claude Code rules in Section 16 so it works one phase at a time and stops for review.

**Decision:** Accepted

**Informed:** `DESIGN.md` Sections 15 and 16. End of design phase; the build continues in Claude Code.

---

### Entry 14: P0 becomes shared between user and Claude Code

**Prompt:**
> wait, adjustment on the P0 - i just want to create an empty repo, can you do the rest. so it would be a mix of me and claude code. make the changes and then i start

**Response summary:** Updated the P0 row in `DESIGN.md`. The user creates an empty repo and opens it in IntelliJ. Claude Code generates `pom.xml` (JDK 21, JUnit 5, Surefire 3.x), the package folders, `.gitignore`, and a trivial test. Done when `mvn test` passes and the user has reviewed `pom.xml`.

**Decision:** Accepted

**Informed:** `DESIGN.md` Section 15, P0 row.

---

## Phase 2: Build (Claude Code in IntelliJ)

<!-- For each phase, add: prompt, response summary, decision (accepted/edited/rejected), verification (test output, diff review, manual checks). -->

### Entry 15: P0 Skeleton

**Prompt:**
> okay. so do make sure to keep logging in the ai_prompt_log markdown file. also, i have added the original problem statement pdf in case we need it in future. other than that, we are good to go. P0 is in progress - the boilerplate, i have only initialized an empty repo in intellij using the new project option and group id as com.gopal and the artifactId as booking-service by defaults, change them back to more generic ones as per the plan. add the plugin requirement in pom.xml as required, update the .gitignore etc and whatever you suggested. i will make commits as per my preferences, since these are not required by the assessment, and just for my reference. i will make my first commit after p0 is done btw. also, changes are approved by me

(Before this, Claude Code was asked to read `DESIGN.md` and this log and summarise its understanding without changing anything. It summarised the design and listed the P0 gaps: bare `pom.xml`, no `booking` package, `.gitignore` not ignoring `.idea/`, `.idea` files staged, and two doc mismatches.)

**Response summary:**
- `pom.xml`: coordinates changed from `com.gopal:booking-service` to `booking:appointment-booking`, matching the `booking` package and the project name in DESIGN §14. Switched `maven.compiler.source/target` to `maven.compiler.release` 21. Added `junit-jupiter` 5.11.4 (test scope), `maven-compiler-plugin` 3.13.0 and `maven-surefire-plugin` 3.5.2. `exec-maven-plugin` left for P4, as planned.
- `.gitignore`: replaced the IntelliJ template with a short list: `target/`, `.idea/`, `*.iml`/`*.iws`/`*.ipr`, `.vscode/`, `.DS_Store`.
- Untracked the already-staged `.idea/` files with `git rm -r --cached .idea` (the files stay on disk).
- Created `src/main/java/booking/` (with a `.gitkeep` so git tracks it until P1) and `src/test/java/booking/SmokeTest.java`, one trivial test. Removed the empty `src/main/resources/` and `.mvn/` folders.
- `DESIGN.md`: §1 now says the design is logged in entries 1 to 14 (it said 1 to 8). The root of the §14 layout tree is now `booking-service/`, the real repo name, with a note giving the Maven coordinates.
- The problem statement PDF wasn't found in the repo folder when this ran.

**Decision:** Accepted

**Verification:**
- `mvn test` → `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS` (JDK 21.0.9, Maven 3.9.14).
- User reviewed the changes to `.gitignore`, `pom.xml`, the `.md` files and the added `SmokeTest`, and accepted them.
- At the user's request, re-ran from a clean build: `mvn clean test` → `Running booking.SmokeTest`, `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`.
- From here the user runs Claude Code in manual mode and approves each change before it's applied.

**P0 gate:** met. `mvn test` passes and the user has reviewed `pom.xml`.

---

### Entry 16: P1 Model and store

**Prompt:**
> okay, seems good. um, not testing this one for now. um, P1 - addition of the models and store - do it, already made the commit

**Response summary:**
- `Status`: enum, `ACTIVE` / `CANCELLED`.
- `Appointment`: record (`id`, `providerId`, `start`, `end`, `status`). `cancel()` returns a new record with status CANCELLED; the original is unchanged.
- `BookingResult`: sealed interface with nested `Booked(Appointment)`, `Rejected(Reason, message)` and `Reason` (`INVALID_INPUT`, `CONFLICT`). DESIGN §8 allows nesting them.
- `CancelResult`: enum, `CANCELLED` / `NOT_FOUND` / `ALREADY_CANCELLED` / `INVALID_INPUT`.
- `InMemoryAppointmentStore`: `byId` (`Map<String, Appointment>`) and `byProvider` (`Map<String, List<String>>`, ids only). It has `save`, `findById` (returns `Optional`) and `findByProvider` (returns an unmodifiable list in booking order, ACTIVE and CANCELLED included, empty for an unknown provider). No validation and no business rules.
- `InMemoryAppointmentStoreTest`, 5 tests: save + find by id; unknown id and unknown provider return empty; find by provider returns only that provider's appointments, in order; saving the same id replaces the record without a duplicate index entry; `cancel()` returns a new record and leaves the original unchanged.
- Removed the P0 placeholders (`SmokeTest.java`, `src/main/java/booking/.gitkeep`).
- Kept `byProvider` ids on cancel, the "current choice" in DESIGN §9. Nothing in P1 removes ids, and the ACTIVE filter will go in the P2 overlap check.

**Implementation note (equivalent to the spec, worth a review):**
- DESIGN §9 says `save` "adds `a.id()` to `byProvider` if it isn't already there". The code checks whether `byId.put` returned no previous record, and indexes only then. Since an id always belongs to one provider, this is the same as the spec. It is O(1), where a `List.contains` check is O(n).
- It assumes that a re-save never changes `providerId`. Nothing in the service does that (cancel keeps the provider).

**Self-correction:** the first draft of the store's Javadoc linked `{@link BookingService}`, which is an upward reference to a class that doesn't exist yet. Changed it to `{@code BookingService}`.

**Decision:** Accepted

**Verification:**
- `mvn clean test` → `Running booking.InMemoryAppointmentStoreTest`, `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`. Re-run after Entry 17 with the same result.
- User reviewed the P1 code and questioned the id format and replace-on-save (Entries 17 and 18). The user's own test run is deferred until later.

**P1 gate:** met. It compiles and the store tests pass.

---

### Entry 17: Clarify replace-on-save and where cancel logic lives

**Prompt:**
> okay, seems good. One thing i noticed here  - in the save method - we have the option to replace an existing appointment by Id - this is for when we cancel an appointment right? and the cancellation logic itself will live within the BookingService?

**Response summary:**
- Yes. Replace-on-save exists for cancellation. Records are immutable, so `BookingService.cancel` will load the appointment, call `appointment.cancel()` to get a CANCELLED copy, and `store.save(copy)` replaces it under the same id. `byProvider` holds ids, so it needs no update.
- All cancellation rules (blank id → INVALID_INPUT, missing → NOT_FOUND, already cancelled → ALREADY_CANCELLED, otherwise save → CANCELLED) go in `BookingService.cancel` in P3. The store's `save` is a plain write with no knowledge of why. `Appointment.cancel()` only builds the copy.
- New bookings get fresh UUIDs, so in practice only `cancel` replaces a record.

**Decision:** Accepted

**Informed:** Confirms the responsibility split for P3, with cancel rules in the service and a store that only stores.

---

### Entry 18: Review of InMemoryAppointmentStoreTest, id format and restricting updates

**Prompt:**
> In the InMemoryAppointmentStoreTest i have few doubts: 1) in test we are giving random strings as the data type for the the id? what would be the format of the ID and how do we keep them unique? same for the providerId.2) in savingSameIdReplacesRecordWithoutDuplicatingIndex, it feels we are allowing the updation of existing record, while the updation should be limited to only cancelling an existing appointment, and no other way to change the appointment - basically the save method should be accessible by the BookingService and there it uses it only to update the status to cancel, isn't it. tell me your opinion

**Response summary:**
- Appointment ids: generated by `BookingService.book` (P2) as `UUID.randomUUID().toString()`, per DESIGN §6. A random UUID has 122 random bits, so collisions are practically impossible, and no registry is needed. The store treats ids as opaque keys, so the store test uses short ids (`"a1"`) for readability. The id format will be checked in the P2 service tests.
- Provider ids: supplied by the caller (CLI or tests), any non-blank string. There is no provider registry (out of scope). The same string means the same provider. The service only rejects a blank one (T7).
- Restricting updates: agreed that cancel is the only allowed change, but it is enforced in the service layer, not the store:
  - Callers only get `BookingService`, which offers `book` and `cancel` and no update.
  - `Appointment` is immutable, and its only "change" is `cancel()`.
  - `save` is called only by `book` (new UUID) and `cancel` (the cancelled copy).
  - Rule-checking in the store would break DESIGN §7/§16 (the store stays dumb).
  - Gap noted: in one package, Java can't limit `save` to `BookingService`.
- Recommended: keep `save` as is, and possibly rename the test to `savingCancelledCopyReplacesActiveRecordWithoutDuplicatingIndex` so it states the one real use case.
- Stricter option offered and not recommended: `BookingService` creates its own private store, a deviation from DESIGN §8.

**Decision:** Accepted (keep `save` as is; no stricter option). The user understood the provider-id point and is fine with the saving approach.
- Test rename: suggested but not applied. The user didn't confirm it.

**Follow-up prompt:**
> okay, so the provider ids are provided in the requests themselves, and the system doesn;t generate them so we don't need to care about them, got it. also good with the saving question. um, did you run the tests?

**Verification:** Re-ran `mvn clean test` → `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`.

---

### Entry 19: P2 Booking (`BookingService.book`)

**Prompt:**
> next - P2 - the BookingService.book service API - right?

**Response summary:**
- `BookingService(InMemoryAppointmentStore store)`, with the store passed in through the constructor (DESIGN §8/§10).
- `book(providerId, start, end)` follows DESIGN §10.1 in order:
  1. Validate, with no store access: blank/null provider → `providerId is required`. Blank/null start or end → `start and end are required`. No `Z` suffix or unparseable → `timestamps must be ISO-8601 UTC, e.g. 2026-11-20T10:00:00Z`. `end <= start` → `end must be after start`. Each returns `Rejected(INVALID_INPUT, …)`.
  2. Overlap check: the provider's ACTIVE appointments where `existing.start < end && start < existing.end`. The first match → `Rejected(CONFLICT, "overlaps appointment <id>")`.
  3. Create `Appointment(UUID.randomUUID(), …, ACTIVE)`, then 4. `store.save`, then 5. return `Booked`.
  - Steps 2 to 4 sit together in one method, ready for the P5 lock.
- `cancel` and `listForProvider` are not added (P3).
- `BookingServiceTest` (fresh store + service per test, fixed timestamps):
  - T1 valid booking → `Booked`. The id is a valid UUID, the fields match, and it is stored as ACTIVE.
  - T2 same-provider overlap → `Rejected(CONFLICT)` with the conflicting id, and the store is unchanged.
  - T3 starts exactly when another ends → `Booked`.
  - T4 same interval for two providers → both `Booked`.
  - T7, a parameterized test with 9 cases: null provider, blank provider, null start, blank end, unparseable timestamp, date with no time part, `+05:30` offset, zero duration, negative duration. Each case asserts `INVALID_INPUT`, the exact message, and that the store is unchanged.
- Small scope pull-forward: the `+05:30`, zero-duration and negative-duration cases are listed as stretch tests in DESIGN §13. They went into T7 here because the P2 gate requires checking the `Z` check, and they add only rows, not new tests. The other stretch cases (partial/contained/identical overlap, reverse adjacency) stay in P6.
- Not trimmed: `providerId` is stored as given, so `" dr-smith"` and `"dr-smith"` are different providers. DESIGN §6 only requires non-blank. Flagged for review.

**Decision:** Accepted

**Verification:**
- `mvn clean test` → `BookingServiceTest` 13 run (4 + 9 parameterized), `InMemoryAppointmentStoreTest` 5 run. In total, `Tests run: 18, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`.
- JDK behaviour check (DESIGN §10.1 note): in jshell on JDK 21, `Instant.parse("2026-11-20T16:30:00+05:30")` returns `2026-11-20T11:00:00Z`, so offsets are accepted and the explicit `Z` check is needed.
- Mutation checks, run on throwaway copies in the scratchpad (not the repo):
  - Disabled the `Z` check → 1 failure, T7 case [7] "offset instead of Z".
  - Changed the overlap to inclusive (`existing.end >= start`) → 1 failure, `bookingThatStartsWhenAnotherEndsIsAllowed` (T3).
  - So the tests catch the two most likely invariant bugs.
- User check of the overlap condition and the `Z` check (P2 gate): done. The user reviewed `BookingService` ("we will reject any invalid time frames, formats, etc and overalp check being done"), added two comments by hand (Entry 20), and then reviewed `BookingServiceTest` ("went through the tests, seem okay").
- Re-ran `mvn clean test` after the user's comment edits: `Tests run: 18, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`.

**P2 gate:** met. The tests are green, and the user has checked the overlap condition and the `Z` check.

---

### Entry 20: Add the "no overlap" form to DESIGN §5

**Prompt:**
> okay, i have checked the BookingService code, seems fine, we will reject any invalid time frames, formats, etc and overalp check being done. Before checking the test cases, one change in the design doc, just for my reference - for overlap, currently, we are checking: a.start < b.end  &&  b.start < a.end, right? which is fine and i understand this. another logic we can use (when they don't overlap i mean): a.end<=b.start || b.end<=a.start. i would just like you to mention it in the design doc, just more intrinsic understanding for me. let me check the tests

**Response summary:**
- Confirmed: the code checks `a.start < b.end && b.start < a.end`.
- Added the user's "no overlap" form `a.end <= b.start || b.end <= a.start` to DESIGN §5. It is shown as the exact negation of the overlap condition (De Morgan), with a small ASCII sketch of the two "no overlap" cases and the overlap case. It is noted as an alternative; the code keeps the overlap form.
- Code is unchanged.

**Decision:** Accepted

**Informed:** DESIGN §5, reference only.

**Verification:** Checked the equivalence by hand. `!(a.start < b.end)` is `b.end <= a.start`, and `!(b.start < a.end)` is `a.end <= b.start`. This form also allows adjacency: if `a.end == b.start`, then `a.end <= b.start` is true, so there is no overlap.

**Related, from the user's review of `BookingService` (Entry 19):** the user read the code and found the validation and overlap check fine. The user added two comments by hand in `BookingService.book`: `//negative time period` on the `end <= start` check, and `//this returns a conflicted appointment` above the overlap stream.

---

### Entry 21: P3 Cancellation (`BookingService.cancel`, `listForProvider`)

**Prompt:**
> next, P3 - cancel api

**Response summary:**
- `cancel(appointmentId)` follows DESIGN §10.2 in order:
  1. Blank/null id → `INVALID_INPUT`.
  2. Not in the store → `NOT_FOUND`.
  3. Status already CANCELLED → `ALREADY_CANCELLED`.
  4. `store.save(appointment.cancel())`, which replaces the record under the same id (Entries 17/18).
  5. Return `CANCELLED`.
  - Only the CANCELLED path writes to the store.
- `listForProvider(providerId)`: a read-only pass-through to `store.findByProvider`, returning ACTIVE and CANCELLED appointments in booking order. It is for the REPL and debugging (DESIGN §10, Entry 8).
- New tests in `BookingServiceTest`:
  - T5 `cancelledAppointmentFreesItsSlotForRebooking`: book, cancel → `CANCELLED`, then rebook the same slot → `Booked`. The original is stored as CANCELLED, the replacement is ACTIVE, and `listForProvider` shows both in order.
  - T6 `cancellingMissingIdReturnsNotFound`: a random UUID → `NOT_FOUND`, and the store is unchanged.
  - T6 `cancellingTwiceReturnsAlreadyCancelled`: the second cancel → `ALREADY_CANCELLED`, and the store still holds one CANCELLED record.
  - `cancellingWithBlankIdReturnsInvalidInput` (AC5, missing identifier): null, empty and whitespace → `INVALID_INPUT`, and the store is unchanged. This is not a numbered test in DESIGN §13. It was added because AC5 covers missing identifiers, and `INVALID_INPUT` was added to `CancelResult` for exactly this case (Entry 9).
- Kept the id in `byProvider` on cancel, and the overlap check filters on `status == ACTIVE`. This settles the open decision in DESIGN §9 on the "current choice", in line with the user's "one source of truth" principle (Entries 6/7). The DESIGN §9 text is not yet updated to say it's settled.

**Decision:** Accepted

**Verification:**
- `mvn clean test` → `BookingServiceTest` 19 run, `InMemoryAppointmentStoreTest` 5 run. In total, `Tests run: 24, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS` (P3 gate: full suite green).
- Mutation checks, run on throwaway copies in the scratchpad (not the repo):
  - Removed the ACTIVE filter from the overlap check → 1 failure, `cancelledAppointmentFreesItsSlotForRebooking` (T5).
  - Removed the ALREADY_CANCELLED check → 1 failure, `cancellingTwiceReturnsAlreadyCancelled` (T6).
- User review: done. The user asked about `save`'s `previous == null` / `computeIfAbsent` logic (Entry 22), asked for a re-run (24/24 green), then accepted ("seems fine").

**P3 gate:** met. The full suite is green.

---

### Entry 22: Clarify `previous == null` and `computeIfAbsent` in `InMemoryAppointmentStore.save`

**Prompt:**
> one doubt here, associated with the save method of the InMemoryAppointmentStore. if (previous == null) {
>             byProvider.computeIfAbsent(appointment.providerId(), p -> new ArrayList<>()).add(appointment.id());
>         }

**Response summary:**
- `byId.put` returns the previous record for that id, or `null` if there wasn't one. `null` means this is a first save (`book`); a value means a replace (`cancel`).
- The id is added to `byProvider` only on the first save. On cancel, the id is already indexed, and adding it again would make `findByProvider` return the appointment twice. This is covered by `savingSameIdReplacesRecordWithoutDuplicatingIndex`.
- `computeIfAbsent(providerId, p -> new ArrayList<>())` returns the provider's id list and creates an empty one on the provider's first booking. `.add(id)` appends to it. The longhand `get` / `null` check / `put` equivalent was shown.
- A walkthrough table (book A1, book A2, cancel A1) showed `byId` and `byProvider` after each call.
- Restated the assumption: a re-save never changes `providerId` (`cancel()` keeps every field but status).

**Decision:** Accepted (explanation only, no code change)

---

### Entry 23: P4 CLI REPL and demo

**Prompt:**
> seems fine. let's move to the CLI portion and demo. explain it to me in detail as well

**Response summary:**
- Marked Entry 21 (P3) Accepted.
- `Main` (`src/main/java/booking/Main.java`):
  - `main` wires `new BookingService(new InMemoryAppointmentStore())`, so state lives for the whole session. It then runs a read-eval-print loop on stdin.
  - The loop prints a `> ` prompt (flushed), reads a line, trims it, and splits it on whitespace. A blank line is skipped. End of input (Ctrl-D) or `exit` ends the session.
  - Commands follow DESIGN §11: `book <providerId> <start> <end>`, `cancel <id>`, `list <providerId>`, `help`, `exit`. A wrong argument count prints a usage line, and an unknown command prints a hint.
  - The CLI does no validation of its own and passes the raw strings to the service (§11).
  - `book` output uses an exhaustive `switch` with pattern matching over the sealed `BookingResult` (`case Booked` / `case Rejected`), with no `default`. If a new result type is ever added, this stops compiling until it is handled.
  - `cancel` prints the `CancelResult` name. `list` prints `id  start to end  STATUS`, or `no appointments for <provider>`.
  - The constructor is package-private and takes a `PrintStream`, so the CLI could be tested later without touching `System.out`. No CLI tests were added, since DESIGN §7 says tests target the service.
- `pom.xml`: added `exec-maven-plugin` 3.5.0 with `mainClass` `booking.Main`, so `mvn -q compile exec:java` works without `-Dexec.mainClass` (the §14 command with the flag still works).
- Small deviations from the §11 sample output:
  - Full appointment ids are printed (the sample shows `3f2a...`), because `cancel` needs the full id.
  - Full ISO timestamps are printed (the sample shows `10:00Z`), so the date is visible.
  - `exit` prints `bye`.
- Demo transcript saved to `demo-transcript.txt`. It was generated by a scripted driver in the Claude Code scratchpad (`demo_driver.py`, not part of the repo). The driver types commands into `java -cp target/classes booking.Main` and reads each booked id from the output so it can cancel it later; a fixed piped script can't know the random UUIDs.
- First driver run failed (driver bug, not app bug): it took any `> ` as the prompt, and the help text contains `<end> `. Fixed it to match `> ` only at the start of a line.

**Decision:** Accepted

**Verification:**
- `mvn clean test` → `Tests run: 24, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`. No regressions.
- `printf 'help\n\nbook dr-smith\nfoo\nlist dr-nobody\nexit\n' | mvn -q compile exec:java` → help shown, blank line skipped, usage line, unknown-command hint, `no appointments for dr-nobody`, `bye`. The REPL runs through Maven.
- `demo-transcript.txt` shows the P4 gate flow (book, conflict, cancel, rebook, list) plus adjacency, a different provider, the offset/zero-duration/garbage rejections, `ALREADY_CANCELLED`, `NOT_FOUND`, an unknown provider, a usage error and an unknown command. Exit code 0.
- User's own manual demo: done. The user ran the REPL by hand and saved that session in a separate transcript of their own.
- User observation from the manual demo: an empty id for `cancel` never reaches the service. The CLI splits on whitespace, so `cancel` or `cancel   ` has 1 word and prints the usage line. `CancelResult.INVALID_INPUT` (and blank-field `book` rejections) are unreachable through the CLI. They still matter at the service boundary (AC5 applies to any caller), and the service tests cover them directly. The user decided no change is needed.

**P4 gate:** met. The manual demo works (book, conflict, cancel, rebook, list), and transcripts are saved: `demo-transcript.txt` (scripted) plus the user's own manual one.

---

### Entry 24: What is left after P4

**Prompt:**
> now, concurrency is the only thing we left before tightning the docs and preparing any remaining deliverables, right?

**Response summary:**
- Confirmed: P5 (concurrency) is the last feature work, then P6 (hardening and docs).
- P6 items:
  - stretch tests not yet done (partial overlap, containment, identical interval, reverse adjacency)
  - README (setup/test/run, assumptions, invariant)
  - finish DESIGN §17 limitations
  - final log pass
- Loose ends listed:
  - settle the DESIGN §9 open-decision text
  - the optional P1 test rename
  - the assessment PDF is not in the repo
  - where the user's manual transcript lives
  - `list` shows booking order, not start time
- Checked against the deliverables from Entry 1: all are covered by P5/P6.

**Decision:** Accepted

---

### Entry 25: Explain the concurrency plan in DESIGN §12

**Prompt:**
> okay, let's discuss more about concurrency. what does the design doc say, explain to me

**Response summary:**
- Restated §12: a check-then-act risk; one `ReentrantLock` or `synchronized` around `book` steps 2 to 4 and all of `cancel`; validation outside; per-provider locks as an optional upgrade; an N-thread test with `ExecutorService` + `CountDownLatch`; and the gate that it must fail without the lock.
- Walked through the race in our code (two threads both pass the overlap check before either saves).
- Raised a point §12 didn't spell out: the store's `HashMap` / `ArrayList` are not thread-safe. Concurrent writes can lose ids or throw `ConcurrentModificationException`, and writes may not be visible across threads, even for non-conflicting bookings. The single service lock also fixes this, so the store can stay dumb.
- Noted the same race in `cancel` (both callers get `CANCELLED`).
- Explained why validation can run outside the lock (method-local, immutable data).
- Asked for four decisions, each with a recommendation:
  1. `ReentrantLock` vs `synchronized` (recommended `ReentrantLock`; not a `synchronized` method).
  2. Lock `listForProvider` too (recommended yes).
  3. A repeated stress test (about 50 threads × about 200 rounds).
  4. Single lock vs per-provider locks (recommended a single lock, plus a limitation note).

**Decision:** Accepted

---

### Entry 26: Concurrency decisions; premature edit rejected

**Prompt:**
> okay, got it. My choices and resoning:1) let's just use reentrantlocks here. 2) make listForProvider locked too. 3) for testing, i think we can use executorService to make enough threads to show that it breaks when locks not used. 4) I am confused about the per-provider locks. they make sense because without them, two threads booking on separate providers would be synchronized, isn't it? I do get the fact that we would need to make the maps inside the memmoryStore concurrent as well and the final method would also need to get the providerId and the corresponding lock. so a little confused about that. I think we can go with your suggested idea - let's keep it simple, it keeps things simple and correct and we can put this as a known limitation. what do you say?

**Response summary:**
- Confirmed the user's reading of per-provider locks: with one lock, bookings for different providers wait for each other although they can't conflict. That is a throughput cost only.
- Per-provider locks would need a thread-safe store, plus a look-up-provider-then-re-check step in `cancel`. Agreed to keep one lock, with a limitation note.
- Claude Code then **started editing `BookingService.java`** (adding the lock to `book`, `cancel`, `listForProvider`) without first restating the final decisions and getting confirmation.

**Decision:** **Rejected** (the code edits). The user rejected the edit: "don;t make the chages yet,s till discussing". No change was written; `BookingService.java` is unchanged.

**Follow-up prompt:**
> give me the final decisions with the concurrency first. Then only make the changes

**Follow-up response summary:** Listed the final P5 decisions, with no edits:
1. One `private final ReentrantLock`, always `lock(); try { … } finally { unlock(); }`.
2. `book`: validation outside; overlap check + create + save inside.
3. `cancel`: blank check outside; find + status checks + save inside.
4. `listForProvider` locked; `findByProvider` returns a snapshot.
5. The store stays plain collections, guarded by the service lock.
6. No per-provider locks (limitation in §17).
7. A new `BookingServiceConcurrencyTest`: 50-thread pool; fresh service per round; thread *i* books `10:00+i min` to `11:00+i min` (all pairwise overlapping); a ready latch and a go latch; each round must give exactly 1 `Booked`, 49 `CONFLICT` and 1 stored appointment, with any exception failing the test; about 200 rounds.
8. Proof: remove the lock in a throwaway copy and confirm the test fails.
9. Update DESIGN §12/§17 and this log.

**Decision:** Accepted, pending implementation. The user asked for the docs to be updated first (Entry 27).

**Lesson noted:** during a design discussion, restate the final decisions and wait for an explicit go-ahead before editing code.

---

### Entry 27: Bring DESIGN.md up to date before P5 code

**Prompt:**
> okay, first make the changes to the AI logs and design doc with any missing updates.

**Response summary:** Docs only, no code changes. `DESIGN.md` updates:
- **Header status:** now "In build. P0 to P4 done; P5 decided, not yet implemented".
- **§6 Threading:** "Phase 1/Phase 2" → "single-threaded through P4, P5 adds one lock".
- **§8 Classes:** added `InMemoryAppointmentStoreTest` and `BookingServiceConcurrencyTest` (P5). `BookingServiceTest` now notes T1 to T7 + blank-id cancel.
- **§9 Store:**
  - The open decision is marked **Decided (P3)**: keep ids in `byProvider`, filter on ACTIVE. Removing them is recorded as the rejected alternative.
  - `save` row updated to "index only on first save".
  - Added the re-save-keeps-`providerId` assumption.
  - Added a note that the store isn't thread-safe and relies on the service lock.
- **§10.1:** "Phase 2" → "P5". The JDK `Instant.parse` note is changed from "to verify" to **verified in P2**, with the jshell result and the T7 offset case.
- **§11 CLI:**
  - The sample output now matches the built REPL (full ids, full timestamps, `bye`), with pointers to `demo-transcript.txt` and `demo_transcript_manual.txt`.
  - Added the user's finding that blank args become CLI usage errors, so `INVALID_INPUT` is reachable only through the service API.
  - Noted that `list` is in booking order.
- **§12 Concurrency:** rewritten as "Concurrency (P5)" with three risks (book race with a diagram, cancel race, non-thread-safe store), the decision table from Entry 26 with reasons, the one-lock vs per-provider trade-off, and the concrete test design and gate.
- **§13 Tests:**
  - T7 row expanded to the 9 cases actually built. Added the extra blank-id cancel row.
  - Recorded what's done and that three stretch cases were pulled into T7.
  - Moved the concurrency test out of stretch, since the P5 gate requires it.
  - Listed the remaining stretch tests for P6.
  - Listed the four mutation checks done so far.
- **§14 Layout:** added both demo transcripts and all three test classes, the actual `pom.xml` contents, and the REPL command without `-Dexec.mainClass`.
- **§17 Limitations:** expanded the single-lock bullet with the reason per-provider locks weren't used. Added the CLI blank-argument note.

**Decision:** Accepted_

**Verification:** Docs-only change; no code was touched. P5 code waits for the user's go-ahead.

---

### Entry 28: P5 Concurrency

**Prompt:**
> Okay, now make the changes to for concurrency handling

**Response summary:** Implemented the decisions from Entry 26 / DESIGN §12.
- `BookingService`: added one `private final ReentrantLock lock`. Every use is `lock.lock(); try { … } finally { lock.unlock(); }`.
  - `book`: validation unchanged and outside the lock. The overlap check, create and `save` are inside.
  - `cancel`: the blank-id check is outside. `findById`, the NOT_FOUND / ALREADY_CANCELLED checks and `save(cancel())` are inside.
  - `listForProvider`: `store.findByProvider` runs inside the lock. It returns a snapshot (`toList()`).
  - Class and field Javadoc updated (thread-safety note; the lock serializes every provider, see §12).
  - The user's own inline comments were kept.
- `InMemoryAppointmentStore`: unchanged (Decision 5).
- New `BookingServiceConcurrencyTest`, with a fixed pool of 50 threads and a fresh service per round, 200 rounds per test:
  - `concurrentOverlappingBookingsForOneProviderLetExactlyOneSucceed`: thread *i* books `dr-smith` for `10:00 + i min` to `11:00 + i min`, so every pair overlaps. A ready latch and a go latch release all threads together. Each round asserts exactly 1 `Booked`, all others `Rejected(CONFLICT)`, and 1 stored appointment. A thread exception surfaces through `Future.get` and fails the test.
  - `concurrentCancelsOfOneAppointmentLetExactlyOneSucceed`: **not in the agreed test list** (Entry 26 item 7 covered only `book`). It was added because Decision 3 locks `cancel` to close the double-cancel race, and nothing else tests that. 50 threads cancel one appointment; each round asserts exactly 1 `CANCELLED` and all others `ALREADY_CANCELLED`. Flagged to the user after it was written; can be removed if not wanted.

**Decision:** Accepted, and keep the cancel concurrency test

**Verification:**
- `mvn clean test` → `BookingServiceTest` 19, `BookingServiceConcurrencyTest` 2 (0.44 s), `InMemoryAppointmentStoreTest` 5. In total, `Tests run: 26, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`.
- With the lock, the concurrency tests were run 5 more times (`mvn -q test -Dtest=BookingServiceConcurrencyTest`): 5/5 pass.
- **Gate: fails without the lock.** On a throwaway copy in the scratchpad (not the repo), all 6 `lock()`/`unlock()` calls were commented out, and the concurrency tests run 3 times:
  - Booking test: failed in round 1 on all 3 runs, with `expected exactly one booking to win ==> expected: <1> but was: <50>`. Every thread booked an overlapping slot.
  - Cancel test: failed on all 3 runs (round 1, 13, 1), with `expected exactly one cancel to win ==> expected: <1> but was: <2>`.
  - `Tests run: 2, Failures: 2` each time.
- User review: the user read and understood both concurrency tests, and added their own comments to `BookingServiceConcurrencyTest`. At the user's request, the full suite was re-run with the locks present (all 6 `lock()`/`unlock()` calls checked first): `Tests run: 26, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`. The user then committed.
- **Independent verification by the user:** after committing, the user commented out the 6 `lock()`/`unlock()` lines in the real `BookingService.java` (in IntelliJ) and ran `BookingServiceConcurrencyTest`:
  - `concurrentOverlappingBookingsForOneProviderLetExactlyOneSucceed:56 round 1: expected exactly one booking to win ==> expected: <1> but was: <50>`
  - `concurrentCancelsOfOneAppointmentLetExactlyOneSucceed:132 round 1: expected exactly one cancel to win ==> expected: <1> but was: <3>`
  - `Tests run: 2, Failures: 2, Errors: 0, Skipped: 0`
  - The full output is saved at the user's request in `concurrency-no-lock-failure.txt`, with a short context header and the output unedited.
  - This matches Claude Code's scratch-copy result (bookings `<50>` every run; cancels `<2>`, and `<3>` here, since cancel race timing varies). The edit is to be reverted with `git checkout src/main/java/booking/BookingService.java` (the committed version has the locks).

**P5 gate:** met. The tests pass with the lock, and fail when the lock is removed, checked both by Claude Code (scratch copy) and by the user (real code).

---

### Entry 29: P6 plan, then first P6 changes

**Prompt (plan):**
> let's just keep the cancel concurrency test. last phase - tightining of docs and compilation of deliverables. don't make any changes, first just tell me what is to be done here?

**Response summary (plan, no changes made):**
- Checked the repo read-only: working tree clean, locks restored and committed (`bacc589`), Entries 27/28 decisions blank, no README, no PDF found.
- Proposed P6 work in six parts:
  1. Close out P5 bookkeeping (log decisions; DESIGN status/§8/§12/§14).
  2. Stretch tests: a 6-case parameterized overlap test, plus an optional mutation check.
  3. A new `README.md`.
  4. Finish DESIGN §17, the status line and §13.
  5. A final AI-log pass (a P6 entry, an optional summary table, no blanks left).
  6. Final verification: `mvn clean test`, a REPL run, a deliverables check.
- Asked five questions: the PDF location, stretch tests, an `evidence/` folder, the optional P1 rename, the log summary table.

**User decisions:**
- Keep the cancel concurrency test (closes Entry 28's open question).
- The problem statement PDF is now in the repo root (`GOPAL GUPTA_AI_Assessment.pdf`).
- **No stretch tests:** "Don't need the stretch tests, already have enough". Plan item 2 is **rejected**.
- Move the text files into `evidence/`.
- Not answered yet: the P1 test rename, the log summary table.

**Prompt (changes):**
> note: i have added the problem statement doc in the root of the folder which i earlier said i would put but forgot. Don't need the stretch tests, already have enough. Move the text files to evidence. do this much

**Response summary (changes):**
- `git mv` of `demo-transcript.txt`, `demo_transcript_manual.txt` and `concurrency-no-lock-failure.txt` into `evidence/`. Git records them as renames, so history is kept. The file names are unchanged.
- `DESIGN.md`: the §11 transcript pointers now use `evidence/…`. The §14 layout now shows the PDF and the `evidence/` folder with all three files, including `concurrency-no-lock-failure.txt`, which was missing before.
- Earlier log entries (23, 27, 28) still name the files at the root. They are left as they were, because they record the state at that time; this entry records the move.
- Nothing else was changed, per "do this much". The PDF was not read or modified.

**Decision:** Accepted

**Verification:** `git status` shows `R` (rename) for all three files into `evidence/`. A grep shows no remaining root-path references in `DESIGN.md`.

---

### Entry 30: Split docs into a reviewer DESIGN.md + README, archive the build spec

**Prompt (opinion):**
> next i think i will keep a readme.md - for most of the things you listed. i am confused if i should add a separate design doc other than the one i made with claude.ai, because it has become too big, i can put the existing one in a separate folder with appropriate name "design_doc_for_claude_ai" or something better. then, the design doc for the reviewers will only contain the needed portion/summarised version -1) proposed/implemented service boundary 2) relevant data/API design 3) booking invariant 4) cncurrency approach 5) persistence approach (CLI) 6) key assumptions or tradeoffs. and then the README.md can contain the following: 1) sinlge line - assessment 2) requirements - JDK 21, Maven 3) setup and run instructions with Commands to run and test 4) A short note describing assumptions and the booking invariant 5) A concise summary of limitations or follow-up risks discovered within scope and any other useful thing. just want to keep this stuff concise for reviewers in the way they want. don't make any changes, tell your opinion

**Response summary (opinion, no changes):**
- Read the assessment PDF (read-only). The user's split matches its deliverables list almost word for word, and the PDF asks that the design spec be "proportionate to this bounded feature".
- Recommendations:
  1. Archive the old spec rather than trimming it, since its phase plan and checks are evidence of task decomposition and review.
  2. Name it `docs/build-spec.md`, not "design_doc_for_claude_ai": it was written *with* claude.ai *for* Claude Code.
  3. Add a note in this log that "DESIGN §N" now means the build spec.
  4. Correction to item 5: persistence means the in-memory store; the CLI belongs under the service boundary.
  5. Keep the README short and link to `DESIGN.md` instead of duplicating it.
  6. README extras: a file map, a verification summary, and a candid "How AI was used" section (the PDF asks to be candid).
  7. Optionally move the PDF to `docs/assessment.pdf`, since its name contained the full name.

**Prompt (changes):**
> okay, seems good, mention in the readme/summariesed design doc clearly about the docs folder - build-spec and what the evidence folder is for. make the changes

**Response summary (changes):**
- `git mv DESIGN.md docs/build-spec.md` (history kept). The PDF was moved to `docs/assessment.pdf` and staged as a rename.
- `docs/build-spec.md`:
  - New header note ("Archived working spec…", with a pointer to `../DESIGN.md`, and §-references in this log mean this file). Status set to complete.
  - §8 and §12 now cover the cancel concurrency test and its result.
  - §13 records the stretch tests as not built, by the user's decision.
  - §14 layout updated (README, the new DESIGN.md, `docs/`).
- New `DESIGN.md` (reviewer spec), with an intro explaining `docs/` (build spec + PDF) and `evidence/`, then the user's six sections:
  1. Service boundary.
  2. Data and API design.
  3. Booking invariant.
  4. Persistence approach (the in-memory store; the CLI is placed under the service boundary).
  5. Concurrency approach.
  6. Key assumptions and trade-offs.
- New `README.md`:
  - The one-line purpose, requirements, setup/run/test commands with a CLI command table and a sample, assumptions and the invariant, limitations and follow-up risks.
  - Plus "How it was verified" (test classes → ACs, mutation checks, no-lock failure, demos), "How AI was used", and a repository layout that explains `docs/` and `evidence/`.
  - A follow-up risk not stated before: the in-process lock does not carry over to a database; there the invariant would need a transaction or exclusion constraint.
- This log: the file-reference note at the top. Entry 29's decision marked Accepted. Earlier entries are left as written.

**Decision:** Edited

**Verification:**
- `mvn clean test` (README command) → `Tests run: 26, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`.
- `mvn -q compile exec:java` (README command), with piped `book` / overlapping `book` / `exit` → `BOOKED …`, `REJECTED  CONFLICT: overlaps appointment …`, `bye`. The README sample output matches the real output.
- `git status` shows renames for `DESIGN.md → docs/build-spec.md` and the PDF `→ docs/assessment.pdf`.

---

### Entry 31: README fixes: dependencies, evidence names and descriptions, own-review examples, short layout

**Prompt:**
> okay, a few changes suggested:1) why havent you listed junit, etc in the requirements, 2) i would like you to clearly mention what each file in the evidence folder is for - probably also change the names to better understandble file names.also, one of the things i pointed early out was the fact that you had forgotten that records won't allow you to directly change the status since enums are immutable inside the immutable records 3) in the repository layout - it is difficult to read since the descriptions are too big, keep them short. do this, one more change has to be added

**Response summary:**
1. **Requirements:** the README listed only what must be installed (JDK, Maven) and left out the Maven-fetched dependencies. It now has a table of what Maven downloads on the first build: JUnit Jupiter 5.11.4 (test scope), `maven-compiler-plugin` 3.13.0, `maven-surefire-plugin` 3.5.2, `exec-maven-plugin` 3.5.0, with what each is for. It also notes that the app has no runtime dependencies beyond the JDK.
2. **Evidence files:**
   - Renamed with `git mv`: `demo-transcript.txt` → `cli-demo-scripted.txt`, `demo_transcript_manual.txt` → `cli-demo-manual.txt`, `concurrency-no-lock-failure.txt` → `concurrency-tests-fail-without-lock.txt`. File contents are unchanged, including the user's manual transcript.
   - The README now has an "Evidence folder" table (file / what it shows / how it was produced). `DESIGN.md`'s intro lists each file in one line.
   - References updated in `DESIGN.md` and `docs/build-spec.md`. The note at the top of this log maps the old names to the new ones; earlier entries are left as written.
3. **Record immutability:** added to the README's "How AI was used" as an example of the user's own review, with Entries 4–5. Precise wording used: record *fields* are final, so the status field can't be reassigned (the enum itself isn't the issue). The fix was `cancel()` returning a new record. The user's other design-phase catch was added too: the missing provider index (Entry 4).
4. **Repository layout:** descriptions shortened to a few words each. `evidence/` points to the "Evidence folder" table instead of repeating it.

**Decision:** Accepted

**Verification:** `git status` shows `R` for the three evidence renames. A grep of `README.md`, `DESIGN.md` and `docs/build-spec.md` finds no old evidence filenames.

**Pending:** the user said one more change is to be added.

---

### Entry 32: Save the user's final full test run as evidence

**Prompt:**
> okay. so i also ran the tests myself, all passed, thinking of storing them in evidence as an appropriate txt file: (pasted `mvn test` output, ending `Tests run: 26, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`, `Finished at: 2026-11-21T17:06:40+05:30`)

**Response summary:**
- Saved as `evidence/all-tests-pass.txt`: a short header (what it is, who ran it and when, a pointer to `concurrency-tests-fail-without-lock.txt` for contrast), then the output unedited.
- `README.md`: added a row to the "Evidence folder" table and a "Full suite" bullet under "How it was verified".
- `DESIGN.md`: added the file to the `evidence/` list in the intro.
- `docs/build-spec.md`: added the file to the §14 layout.

**Decision:** Accepted

**Verification (by the user):** `mvn test` on the final code → `BookingServiceTest` 19, `BookingServiceConcurrencyTest` 2, `InMemoryAppointmentStoreTest` 5. In total, `Tests run: 26, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`.
