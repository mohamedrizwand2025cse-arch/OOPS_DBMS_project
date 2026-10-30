# RuleWeaver X — Project Progress

## A. Project Overview

RuleWeaver X is a JavaFX desktop application for defining versioned decision policies, evaluating applicants with explainable rule results, and comparing policy outcomes. The implementation includes MongoDB persistence and an in-memory repository for service tests/offline initialization.

| Item | Current state |
|---|---|
| Stack | Java 17, JavaFX 20.0.1, Maven, MongoDB Java sync driver 5.12.0, JUnit 5.9.0 |
| Current branch | `main` |
| Latest commit | `e451428` — `Integrate local UI updates with latest main` |
| Working tree | Time Machine history/comparison/audit UI changes are uncommitted alongside Insight Center, Dashboard Stage 2, Applicants Update 3, Policies redesign, and separate T004 migration/index work. |
| Main source/test layout | `src/main/java`, `src/main/resources`, `src/test/java` |

## B. Completed Features

### Time Machine — historical policy and decision inspection

- [x] Inspected `Capture.PNG`, `Capture2.PNG`, and the actual lowercase `capture3.PNG`; added version-history inspection, side-by-side saved-configuration comparison, and a stored decision log within the existing Time Machine route.
- [x] The history and comparison panels display saved policy-version fields/conditions only. The decision log loads persisted results asynchronously by selected policy/version, filters recorded Approved/Rejected/Unknown outcomes, and exposes the stored applicant snapshot, condition results, and actual/required values when present.
- [x] Added explicit empty, loading, error/retry, and offline states. Replay and publisher-change summaries are not represented as available because no matching persisted audit/change data is exposed by the current model.
- [x] Corrected comparison counting so unknown outcomes are not presented as rejections and repeated histories with missing timestamps are omitted when their latest record cannot be established; added regression tests.
- [x] Verified `mvn clean compile` **BUILD SUCCESS** (98 production source files) and `mvn clean test` **BUILD SUCCESS** (94 tests, 0 failures, 0 errors, 0 skipped). `git diff --check` passed.
- [x] Launched JavaFX in offline mode against a loopback-only MongoDB endpoint and visually inspected the existing Time Machine route at 1366x768. The Version History, Compare Versions, and Decision Log tabs all render; navigation to Time Machine and switching between these tabs were exercised.
- [ ] Live Atlas data, saved-record selection, comparison results, and populated audit filters remain unverified because the offline database has no policies, versions, or evaluations. The three workflows displayed their empty/selection states; no records were seeded.
- [x] Launch output showed an existing `.search-bar` stylesheet `ClassCastException` warning in shared CSS; no stylesheet parse errors or missing-resource messages were observed. This unrelated selector was not changed.

### Milestone status — Programs & Applicants visual shell and KPI wiring

- [x] Verified the design reference image is readable and the repo worktree contains existing uncommitted teammate work that was preserved.
- [x] Reused the existing `ProgramManagerView` layout, program filters, dark-navy styling, and service/repository contracts without rewriting unrelated screens.
- [x] Added the visual shell for the Programs & Applicants screen, including the page header, KPI strip, tabs, search/filter toolbar, and filter-chip state handling.
- [x] Wired KPI totals to repo-backed counts for total programs, total applicants, and active programs with async loading and explicit unavailable/error states.
- [x] Verified the build: `mvn clean compile -q` completed successfully; `mvn clean test -q` completed successfully. JavaFX native-loader warnings were logged, but no build or test failures occurred.
- [x] Stopped at this milestone boundary; later stages (cards/table view, drawers, dialogs, and motion polish) remain unimplemented in this checkpoint.

### Programs & Applicants — usability and functionality fixes

- [x] Applicant rows now use a fixed 54px cell size; name/email are compact and ellipsized, skills stay on one line with a full-value tooltip, and numeric columns sort numerically.
- [x] Applicant search now covers name, email, and skills case-insensitively after trimming whitespace. Added pure-Java coverage for those search cases.
- [x] Applicant edit preserves the existing program association and custom attributes. Save/delete refresh errors are surfaced; Mongo update/delete throw when the target record does not exist, preventing success-shaped no-ops.
- [x] Programs render in a compact, fixed-height list with name, template/category, field count, and lifecycle badge. Search/status filtering and existing service-backed create, rename, duplicate, archive, select, and schema-edit paths are retained; unsupported deletion was not added.
- [x] Verified `mvn clean compile` **BUILD SUCCESS** (97 production sources) and `mvn test` **BUILD SUCCESS** (89 tests, 0 failures, 0 errors, 0 skipped). `ApplicantRepositoryTest` ran all 4 tests, including Mongo repository CRUD; new applicant-search tests passed (2/2), and existing program filter/sort and service tests passed.
- [ ] JavaFX visual checks for row density, window resizing, scrolling, and end-user create/edit/delete/program actions still need manual verification. No production records were manually changed.


These items have implementation evidence and automated test evidence. Test evidence does not imply that the JavaFX UI was manually exercised.

- [x] **Applicant Mongo repository CRUD:** insert, read, list, update, and delete are exercised against the configured Mongo test database by `ApplicantRepositoryTest.testApplicantCRUD`.
- [x] **Rule condition serialization and compatibility:** `ConditionDocumentMapper` round-trips nested AND/OR composite rules, accepts legacy condition fields, and defaults legacy composites to AND; covered by `ConditionDocumentMapperTest`.
- [x] **Applicant/evaluation BSON mapping and schema shape:** applicant/evaluation mappers and initializer schema fields are covered by mapper and `DatabaseInitializerSchemaTest` tests.
- [x] **Policy service evaluation-run semantics:** in-memory service tests verify append-only repeated runs, progress reporting, cancellation without partial persistence, immutable versions, and active-version selection (`PolicyServicePersistenceTest`).
- [x] **What-if simulation logic:** `WhatIfSimulatorTest` verifies a threshold change against the latest stored baseline, reports the decision delta/flip, and leaves the original version unchanged.
- [x] **Impact Lab simulator service:** Stage A recalculates base and adjusted policy decisions with the shared `DecisionEngine`, supports compatible numeric/Boolean/multi-select rule adjustments, and reports in-memory count changes and changed applicants; covered by `ImpactLabSimulatorTest`.
- [x] **Dashboard analytics service:** Stage A filters and metrics are calculated from stored evaluations, with distinct-applicant counts, explicit denominators, rejection/rule impacts, near-miss counts, and a paired immediately preceding-version baseline; covered by `DashboardAnalyticsTest`.
- [x] **Mongo collection-level persistence checks:** `ApplicantRepositoryTest` exercises applicant CRUD, unique policy-version numbering, and evaluation document save/read against `ruleweaver_test`. These tests do not cover the complete typed application-to-Atlas workflow.
- [x] **Configurable program schemas:** typed field definitions, seven editable templates, program configuration CRUD/archive, custom applicant attributes, and program-linked policy validation are implemented with unit-test coverage.
- [x] **Policy Studio rule-draft service:** in-memory draft trees support nested AND/OR groups, schema validation, immutable condition generation, and matching natural-language/tree previews; covered by offline tests.

## C. Partially Completed Features

- [/] **JavaFX application and views:** `MainApp`, `ViewRouter`, shared controls, and the Dashboard, Applicants, Policies, Evaluation, and Comparison views are implemented and compile as part of the test build. Dashboard Stage 2, Applicants Update 3, and the Policies redesign refine their screen layouts; JavaFX interaction and visual rendering have not been manually checked for these stages.
- [/] **Policy and evaluation persistence in the running app:** `MongoPolicyRepository`, `PolicyVersionRepository`, `EvaluationRepository`, BSON mappers, and `AppContext` wiring are present. Unit tests cover in-memory service behavior and mapper/schema contracts, while the Atlas tests exercise raw document repository operations; typed policy/evaluation round trips through the full UI are not verified.
- [/] **Dashboard and what-if UI:** `DashboardView` implements Task 2 Stage A analytics plus the Task 3 Stage A Impact Lab program/policy/base-version selectors, up to three numeric threshold editors, Boolean/multi-select controls, asynchronous simulations, and changed-applicant explanations. Stage 2 visual layout work is implemented and compiles/tests; manual interaction/rendering and Atlas-backed refresh have not been verified.
- [/] **Policy rule builder:** `PolicyRuleBuilder` is integrated into new-policy and new-version modals. It supports schema-driven field/operator/value editors, nested AND/OR groups, add/remove/duplicate/reorder actions, group collapse, validation-gated saving, natural-language/tree previews, and a debounced asynchronous unsaved impact preview. Automated tests cover draft tree semantics and validation; JavaFX interaction has not been manually exercised.
- [/] **Policy administration and operations:** policy category/archive fields, version selection, and active-version persistence exist in the model/repository. The Policies screen presents policies, version history, active-version selection, rule details, and existing create/version actions. A complete archive/search/audit workflow, evaluation export, and end-user operation checks are not established by the current code/tests.

## D. Pending Tasks

- [ ] Review Dashboard Stage 2, Applicants Update 3, and the Policies redesign; next, redesign Evaluation, then manually exercise the views, dialogs, rule-builder validation, navigation, evaluation history, comparison, and what-if draft flow.
- [ ] Add integration coverage for `MongoPolicyRepository` and typed `EvaluationRepository.saveAll`/readback, then verify policy and evaluation flows through `AppContext` against a non-production database.
- [x] Add offline decision-consistency, repeated-run analytics, and initialization coverage for Task 0.
- [x] Implement Task 1 program templates, typed field definitions, schema validation, program administration, and offline tests.
- [ ] Decide and implement remaining product scope: policy archive/search/audit and evaluation export.
- [ ] Establish CI and verify platform-specific Maven packaging/launch behavior; the configured shade and JavaFX platform profiles have not been exercised in this update.

## E. Database and Persistence

- **Application database:** `ruleweaver` is the default in `MongoConfig` and in `config.example.properties`; `RULEWEAVER_DB_NAME` can override it. The Java configuration loader does not embed a MongoDB URI.
- **Integration-test database:** `ApplicantRepositoryTest` connects to `ruleweaver_test`, pings it, and removes its generated test records in cleanup.
- **Collections and records:**

| Collection | Persisted shape / purpose |
|---|---|
| `applicants` | `_id`, `name`, `email`, `gpa`, `interviewScore`, `skills`, `status`, and optional `customAttributes`; mapped by `ApplicantDocumentMapper`. |
| `policies` | `_id`, `name`, `description`, `category`, `activeVersionNumber`, and `archived`; saved by `MongoPolicyRepository`. |
| `policy_versions` | Immutable policy snapshots with `_id`, `policyId`, `version`, `description`, `createdAt`, and serialized `conditions`. `ConditionDocumentMapper` handles numeric, skill, and nested composite conditions. Policy removal archives metadata and preserves snapshots. |
| `evaluations` | Append-only evaluation results with applicant/policy IDs and names, optional `evaluationRunId`, `policyVersionId`, `policyVersion`, `overallDecision`, `evaluatedOn`, and per-condition results (including actual/required values and explanation). Legacy records without a run ID remain readable and are shown as untracked history. |
| `programs` | Configurable program templates and field schemas, including type, label, required status, validation rules, permitted values, and supported operators. |

- **Repositories:** Mongo adapters are `ApplicantRepository`, `MongoPolicyRepository`, `PolicyVersionRepository`, and `EvaluationRepository`. In-memory adapters include applicant, policy, and evaluation-result repositories.
- **Versioning:** policy snapshots are inserted rather than replaced. A unique `{policyId, version}` index is created by policy-version repository code and by `DatabaseInitializer`. Active version and archive/category fields are stored on the policy document. Removing a policy archives it; snapshots remain available for historical traceability.
- **Indexes/validation:** `DatabaseInitializer` defines validators for the four collections; it creates a sparse unique `applicantId` index, unique compound policy/version index, and applicant/policy-version evaluation indexes. Updating existing validators uses MongoDB `collMod` and requires appropriate privileges.
- **Evaluation writes:** completed service runs are saved as a batch through `EvaluationResultRepository`; cancellation is tested not to persist partial runs. Results retain the policy name, version identity, timestamp, decision, and per-condition explanations. Demo cleanup removes demo applicants and archives the demo policy without deleting evaluations or snapshots.

## I. Task 1 — Configurable programs and field registry

- Added `NUMBER`, `TEXT`, `BOOLEAN`, and `MULTI_SELECT` field definitions with labels, required status, validation rules, permitted values, and type-compatible operators.
- Added Internship, Scholarship, Campus Placement, Master's Admissions, Hackathon, Training Program, and Custom templates, plus in-memory and MongoDB program repositories and BSON mapping.
- Added dynamic applicant custom-attribute lookup and generic `FieldCondition` evaluation/serialization without introducing a Java model class per custom field.
- Added program administration for creation, template-based creation, rename, field add/edit, duplication, selection, and archive. Program operations and database-backed template initialization run through the existing executor.
- Added program-linked policy schema checks for known fields, condition type/operator/value compatibility, and applicant required/value validation. Existing program-unlinked policies and applicant storage remain supported.
- Schema field removal and type changes fail unless an explicit `ProgramMigration` callback is supplied. This is a guard/extension point, not an automatic Atlas migration; callers must implement and verify data and policy migration before accepting such a change.
- Program schema validation and service behavior have JUnit coverage. Live Atlas persistence and JavaFX interaction were not exercised.

## F. Testing and Build Status

| Command executed during this update | Outcome |
|---|---|
| Task 0 baseline `mvn test` | **BUILD SUCCESS** — 16 tests, 0 failures, 0 errors, 3 Mongo integration tests skipped because no reachable configured test URI was available. |
| Task 0 updated `mvn test` | **BUILD SUCCESS** — 23 tests, 0 failures, 0 errors, 3 Mongo integration tests skipped because no reachable configured test URI was available. |
| Task 1 final `mvn test` | **BUILD SUCCESS** — 38 tests, 0 failures, 0 errors, 3 Mongo integration tests skipped because no reachable configured test URI was available. |
| Task 2 Stage A final `mvn test` | **BUILD SUCCESS** — 44 tests, 0 failures, 0 errors, 3 Atlas-gated integration tests skipped. Compiled 83 production and 14 test Java source files. |
| Task 3 Stage A final `mvn test` | **BUILD SUCCESS** — 50 tests, 0 failures, 0 errors, 3 Atlas-gated integration tests skipped. Compiled 83 production and 15 test Java source files. |
| Task 4 Stage A final `mvn test` | **BUILD SUCCESS** — 58 tests, 0 failures, 0 errors, 3 Atlas-gated integration tests skipped. |
| Dashboard Stage 2 `mvn clean compile` | **BUILD SUCCESS** — compiled 86 production Java source files. |
| Dashboard Stage 2 `mvn clean test` | **BUILD SUCCESS** — 61 tests, 0 failures, 0 errors, 0 skipped; all three `ApplicantRepositoryTest` cases ran and passed. |

The Task 0 final test build compiled 67 production and 10 test Java source files. The Task 1 final test build compiled 82 production and 13 test Java source files. At that checkpoint, package/shaded-JAR creation, JavaFX application startup, and live MongoDB verification had not been run. Later theme-stage checks packaged the shaded JAR and smoke-started the JavaFX application; the Dashboard Stage 2 test run also ran the three Mongo repository tests successfully.
The latest Maven run also reported JavaFX effective-model warnings, the `-source`/`-target` module-path warning (suggesting `--release`), and pre-existing deprecated/unchecked use in `ApplicantsView`. `mvn javafx:run` was not part of this Dashboard stage.

## G. Configuration and Security

- `MongoConfig` checks environment variables first: `RULEWEAVER_MONGO_URI` and `RULEWEAVER_DB_NAME`. If unset, it loads `config.local.properties` from the application directory and then `~/.ruleweaver/config.local.properties`; the application-directory file takes precedence when both define the same setting. The database-name fallback is `ruleweaver`.
- `MongoConnectionDialog` verifies a connection with `ping` before saving settings locally. `MongoConfig` sanitizes Mongo credential text in error messages.
- `config.example.properties` is the safe template: it contains only the URI placeholders `<username>`, `<password>`, and `<cluster-host>`, plus the default database name. No secret values are recorded here.
- `config.local.properties` is intended to remain local and `.gitignore` excludes it; `git check-ignore` confirmed it is ignored. Ignoring a file is not a substitute for avoiding accidental force-adds or committing secrets.
- The local config file contains connection settings and is not reproduced here. Keep its credentials private and rotate them if they remain active. Use a least-privilege account for the application/test databases; reserve `DatabaseInitializer` for an account with required collection/index/`collMod` privileges. If credentials have ever been committed, rotate them and review repository history; this update did not audit or alter Git history.

## H. Recent Changes

- `d6e4731` (2026-10-11), current branch: progress/handoff documentation update; no application source files changed in that commit.
- `da3b224` (2026-10-11): Dashboard rebuild and what-if simulator, rule-builder polish, JavaFX styling/components, and a `WhatIfSimulatorTest`.
- `b185730` (2026-10-11): foundational domain/engine, Mongo configuration and repositories, service layer, JavaFX shell/views, schema initialization, Maven packaging setup, and initial tests.
- Earlier history includes persistence/integration-test work (`4317107`), the `Main.java` entry point (`7001084`), and codebase/domain consolidation (`bc072cd`).

## J. Next Steps

1. **Review and approve Dashboard Stage 2** before proceeding to the Applicants screen.
2. **Run a UI smoke pass** across all views, including the Task 3 Impact Lab controls and asynchronous refresh; first confirm the app can connect using local/environment configuration.
3. **Test typed Atlas persistence end-to-end** for policy snapshots, evaluation batches, restart/readback, and initializer validators using a non-production database.
4. **Close test gaps** for comparison of stored evaluation runs and Mongo policy/evaluation/program mappers and repositories.
5. **Determine an application-level migration strategy** before enabling destructive program-schema migrations.
6. **Add CI and packaging checks** for the supported JavaFX platforms after the test and UI smoke paths are reproducible.

## K. Task 2 — Insight Center Stage A

- Added program, policy, saved-version, evaluation-run, and decision-status filters while retaining existing dashboard and what-if functionality.
- Added testable analytics for evaluated/eligible/rejected counts, pass rate, near-misses, the decision funnel, common rejection reasons, and rule-level impacts; displayed sample sizes and metric denominators.
- Compared a selected saved version with the immediately preceding saved version, pairing by applicant ID. The comparison cohort excludes the decision-status filter; the evaluation-run filter applies only to current results. No causal trend is inferred.
- Assigned a persisted run ID to each service evaluation batch. Legacy records without IDs remain readable and are grouped as untracked rather than assigned fabricated run history.
- Dashboard refresh runs off the JavaFX Application Thread, cancels superseded tasks, ignores stale results, and renders loading, empty, and retryable error states.
- Verification: `mvn test` **BUILD SUCCESS** — 44 tests, 0 failures, 0 errors, 3 Atlas-gated integration tests skipped; `git diff --check` passed. JavaFX interaction and live Atlas behavior remain unverified.
- **Stage B and Stage C are not started; implementation is paused for review as requested.**

## L. Task 3 — Impact Lab Stage A

- Extended the existing dashboard what-if panel into an Impact Lab with program, policy, and base-version selection.
- Added up to three compatible numeric threshold editors with sliders and exact numeric input, plus Boolean selectors and multi-select controls driven by the selected program schema where values are declared.
- Recalculates both the selected saved base version and the adjusted in-memory version over the current applicant cohort using the shared `DecisionEngine`. No evaluation or proposed-policy write is made by the simulator.
- Displays eligible/rejected counts, absolute changes, pass-rate percentage-point change, and applicants whose decision changed with changed condition explanations where determinable. Empty cohorts remain explicit.
- Runs simulations on the existing worker executor with progress, cancellation, error reporting, and generation checks to discard stale results. The existing explicit Policy Studio draft handoff remains available.
- Verification: `mvn test` **BUILD SUCCESS** — 50 tests, 0 failures, 0 errors, 3 Atlas-gated integration tests skipped; `git diff --check` passed. JavaFX interaction and live Atlas behavior remain unverified.
- Applicant records have no program-membership field, so program selection scopes compatible policies/schema controls but does not independently segment the applicant cohort. Task 3 Stage B and Task 4 Stage B are not started.

## M. Task 4 — Policy Studio Stage A

- Replaced the flat-only modal editor with a visual draft tree supporting nested AND/OR groups, condition/group add, removal, duplication, condition reordering, and group collapse/expand.
- Added schema-driven field, operator, and value editors for NUMBER, TEXT, BOOLEAN, and MULTI_SELECT, with a built-in legacy GPA/interview/skills schema when no program is selected.
- Added validation for empty groups, unknown fields, missing/invalid values, unsupported operators, and numeric/schema bounds. Saving remains disabled until the draft is valid.
- Added debounced natural-language and read-only tree previews from the same draft representation used to build engine `Condition` objects.
- Added an asynchronous draft impact preview using the shared `DecisionEngine` and current applicant cohort. New drafts are compared with an empty-policy baseline; version drafts are compared with their source saved version. Results remain in memory and stale/cancelled work is ignored.
- Kept existing create-policy, save-version, and rule-change summary behaviors. Failed policy validation leaves the modal draft intact; no new Stage B version-creation, change-note, or audit workflow was added.
- Offline rule-draft tests cover nested logical evaluation, field types and validation, preview/tree consistency, draft isolation, duplicate rules, impact calculations, and rejected saves without repository writes.
- Task 4 Stage B and Task 5 have not been started; Stage A is paused for review.

## N. UI Theme Modernization and Staged Screen Redesigns

- **Dependency compatibility:** AtlantaFX 2.0.1 requires JavaFX 17 or newer, so it is compatible with JavaFX 20.0.1 and does not require changing the Java 17 baseline. Ikonli 12.3.1 is included with its JavaFX bridge and Material Design 2 pack.
- **Theme and author CSS:** `Theme` selects `PrimerDark` as the sole AtlantaFX user-agent stylesheet entry point. `style.css` remains applied as the scene author stylesheet; default list, combo box, table, scrollbar, dialog, and control styling overrides that conflict with AtlantaFX were removed.
- **Icons:** Replaced in-house SVG path data with Ikonli FontIcons and CSS-backed icon colors. Updated shared components and icon call sites to use the new node type without redesigning later screens.
- **Ikonli startup fix:** Corrected all 20 Material Design 2 literals to use the installed pack's group-specific prefixes (such as `mdi2p-plus`); `IconsTest` covers icon resolution and clear failure for unknown application icon keys.
- **Theme-stage packaging/startup checks:** `mvn package -DskipTests` produced the shaded JAR with `com.ruleweaver.MainLauncher` and `css/style.css`; `mvn javafx:run` started without the icon-resolution exception. The process was stopped after the startup smoke check, so Maven exited with code 1 on termination.
- **Stage completed:** Sidebar and header markup/styles now use AtlantaFX button and typography helpers where applicable, and inline styles were removed from those shell components.
- **Style-specific tests:** No `StyleSheetTest` or `StyleClassUsageTest` exists in `src/test/java`.
- **Dashboard Stage 2:** Reorganized the Dashboard header and actions into a left-aligned wrapping toolbar; standardized dashboard card surfaces and 8px-based spacing; added responsive one/two/three-column KPI rows with wrapping labels and values; clarified analytics, recent-evaluation, decision-funnel, and Impact Lab section headings; and replaced the Dashboard's inline threshold-error color with a CSS class. Existing filters, calculations, charts, asynchronous states, and Impact Lab behavior were retained.
- **Files changed for Dashboard Stage 2:** `DashboardView.java`, `KpiCard.java`, `style.css`, `PROGRESS.md`, and `CHANGELOG.md`.
- **Verification:** `mvn clean compile` **BUILD SUCCESS** (86 production sources); `mvn clean test` **BUILD SUCCESS** (61 tests, 0 failures, 0 errors, 0 skipped). The three `ApplicantRepositoryTest` cases ran and passed. No existing test assertions were changed.
- **Limitations:** Visual verification is **Needs manual check**; this update did not launch or inspect the JavaFX screen. Maven emitted JavaFX effective-model and source/target module-path warnings and pre-existing deprecated/unchecked API warnings in `ApplicantsView`.
- **Applicants Update 3:** Reorganized the page header, search, and existing create action into a wrapping toolbar; added a clearly headed applicant-records card; improved table row spacing, column labels/alignment, wrapping skill chips, row count, and icon button styling; and restyled the existing add/edit form and field errors. Search/filtering, sorting, keyboard shortcuts, CRUD calls, confirmation, validation, feedback, and data were preserved.
- **Applicants validation:** `mvn clean compile` **BUILD SUCCESS** (86 production sources); `mvn clean test` **BUILD SUCCESS** (61 tests, 0 failures, 0 errors, 0 skipped). All three `ApplicantRepositoryTest` cases ran and passed. Maven emitted the existing JavaFX model and module-path warnings and unchecked-operation warning for `ApplicantsView`.
- **Applicants visual limitation:** **Needs manual check**; the UI was not launched or visually inspected for this update.
- **Policies Update:** Reorganized the screen header and new-policy action, restyled the master/detail cards and policy list, improved version-history and condition readability, and added a compact-width stacked layout plus page scrolling. Existing policy selection, version controls, simulator, rule builder, async service calls, and persistence behavior remain unchanged.
- **Policies validation:** `mvn clean compile` **BUILD SUCCESS** (86 production sources); `mvn clean test` **BUILD SUCCESS** (61 tests, 0 failures, 0 errors, 0 skipped). The three `ApplicantRepositoryTest` cases ran and passed. Existing JavaFX effective-model/module-path warnings, MongoDB SLF4J warning, JDK native-access warning, and Applicants unchecked-operation warning remain.
- **Policies visual limitation:** **Needs manual check**; the UI was not launched or visually inspected for this update.
- **Policies manual checklist:** Verify policy selection and wrapping long names at desktop and compact widths; switch saved versions and set one active; inspect version-history status and condition details; exercise What-if, New Policy, and New Version actions and their existing validation/feedback paths.
- **Integrated product plan — T001 baseline/action inventory:** Inspected current UI handlers and documented observed behavior, gaps, exact owners, cross-program scope risks, demo-data copy mismatch, and required workflow tests in `docs/ui-action-inventory.md`. The code was not changed. The previously recorded `mvn clean compile` and `mvn clean test` results (86 production sources; 61 tests passing, 0 skipped) are carried forward, not rerun for this documentation-only task. Manual UI behavior remains unverified.
- **Integrated product plan — T002 data contract:** Drafted `docs/data-ownership-contract.md` after inspecting current models, mappers, repositories, and `PolicyService`. The user approved isolated legacy/unassigned records, immutable schema revisions, saved evaluation context, rebuildable metrics, and dry-run-first migrations as the implementation contract. Applicant hard-delete versus archive remains open. No source/schema or persisted data was changed.
- **Integrated product plan — T003 relationships and snapshots:** Added nullable `Applicant.programId`, `ProgramConfiguration.schemaRevision`, immutable `ProgramSchemaRevision` records with in-memory and Mongo repositories, and compatible program-schema snapshot links on policy versions and evaluation history. BSON mappers preserve unassigned/missing legacy values (schema revision `0`) and round-trip applicant input snapshots and evaluation semantics metadata; old policy-version records inherit program ownership from their parent when loaded through repository lookups. Added mapper and in-memory repository tests. No migration or persisted user-data changes were executed; applicant deletion behavior remains unchanged. `mvn clean compile` **BUILD SUCCESS** (92 production sources); `mvn clean test` **BUILD SUCCESS** (67 tests, 0 failures, 0 errors, 0 skipped). The three `ApplicantRepositoryTest` cases passed. Maven reported the existing JavaFX model/module-path, MongoDB SLF4J, JDK native-access, and Applicants unchecked-operation warnings. No UI was launched; visual behavior remains **Needs manual check**. Program/schema lifecycle wiring, legacy migration, and additional program/evaluation indexes remain for subsequent tasks.
- **Integrated product plan — T004 migration and indexes:** Added `DatabaseMigrationRunner.preview()` (read-only default) and explicit `apply()`, with compatibility preflight, collection pre/post counts, legacy-unassigned counts, a durable completion marker, and retry-safe creation of immutable revision 1 snapshots for programs still at schema revision 0. Conflicting snapshots or dangling positive revision pointers block apply; the runner does not infer or rewrite applicant, policy, policy-version, or evaluation history. Added additive program/status, policy/archive, schema-revision, policy-version, evaluation/run/version indexes to the explicit migration and `DatabaseInitializer`. Migration integration tests use fresh UUID-named temporary databases and drop only those test-created databases. The production/configured application database was not migrated. `mvn clean test` **BUILD SUCCESS** — 69 tests, 0 failures, 0 errors, 0 skipped. Mongo-dependent execution was confirmed: all 3 existing `ApplicantRepositoryTest` cases and both new `DatabaseMigrationRunnerTest` cases ran against MongoDB; the migration tests verified dry-run non-mutation, applied and repeated migration, count/history preservation, unassigned legacy records, index creation, and conflict refusal. `git diff --check` passed. Maven emitted JavaFX effective-model/module-path, MongoDB SLF4J, JDK native-access, and existing Applicants unchecked-operation warnings. **Before any real apply:** take/verify a backup, inspect `preview()` report and conflicts, then call `apply()` explicitly; no production migration has run. Manual verification remains required for a real operator-reviewed dry run and startup/index behavior. Next task: T005.

| Screen stage | Implementation status | Visual result |
|---|---|---|
| Sidebar and header | Theme foundation and shell stage implemented | **Needs manual check** |
| Dashboard / Insight Center | Stage 2 and Insight Center visual layout implemented; awaiting review | **Needs manual check** |
| Applicants | Update 3 redesign implemented; awaiting review | **Needs manual check** |
| Policies | Redesign implemented; awaiting review | **Needs manual check** |
| Evaluation | Not started; awaiting earlier stages | **Needs manual check** |
| Time Machine | Not started; awaiting earlier stages | **Needs manual check** |

## O. Insight Center Visual Layout

- Reworked the existing Dashboard screen into the Insight Center layout without replacing its analytics, filters, asynchronous refresh/error states, rule-outcome analysis, or Impact Lab.
- Added four KPI cards for configured program records, applicant records, saved evaluation decisions, and active policies; all values are sourced from persisted repositories or the existing offline application state.
- Replaced the overview chart with a JavaFX approval/rejection line chart grouped by date over the last 30 days. The chart uses only recognized, timestamped saved decisions; unknown outcomes and undated results are omitted. When no recent valid results are available, it shows an explicit empty state.
- Renamed the existing evaluation-history panel to Recent Activity, added a Data Quality Warnings panel that explicitly reports checks are unavailable (no validated findings source exists yet), and added quick-access buttons wired through the existing view router.
- Updated the shared CSS for the compact KPI layout and chart series colors. No schema, evaluation engine, or unrelated module changes were made for this milestone.
- Verification: `mvn -DskipTests clean compile` **BUILD SUCCESS** — 94 production sources compiled. Maven reported the existing JavaFX model/module-path and Applicants unchecked-operation warnings. `git diff --check` passed.
- JavaFX rendering/navigation have not been manually exercised; visual behavior remains **Needs manual check**.

## P. Insight Center KPI Data Integration

- Connected `Total Programs` to a repository count of configured program records, excluding built-in template records and including archived programs. The MongoDB count executes on the dashboard worker thread.
- Connected `Total Applicants` to MongoDB `countDocuments()` when connected; offline mode uses the already-loaded applicant record list. It does not derive this metric from evaluation history.
- Connected `Decisions Made` to a count of persisted evaluation-result/decision records, so repeated decisions for one applicant are counted as separate saved decisions. MongoDB uses `countDocuments()`; the existing full evaluation read remains only for dashboard analytics and recent activity.
- Defined `Active Policies` as non-archived policies with at least one persisted version, matching the current policy lifecycle (the active-version fallback selects the latest saved version). MongoDB counts using an indexed server-side lookup limited to one matching version; archived and versionless policies are excluded.
- KPI totals are global and are not changed by dashboard filters. They show a skeleton while loading, a literal zero with an empty-data label when empty, and the existing retryable error state on load failure. No historical comparison/delta was added.
- Added in-memory domain tests and MongoDB integration assertions for record counts, built-in template exclusion, archived program inclusion, archived/versionless policy exclusion, and multiple saved decisions per applicant.
- Verification: `mvn clean test` **BUILD SUCCESS** — 71 tests, 0 failures, 0 errors, 0 skipped. MongoDB applicant and migration integration tests ran. Production and test sources compiled; pre-existing JavaFX module-path, SLF4J/native-access, and Applicants unchecked-operation warnings remain. `git diff --check` passed.
- JavaFX visual behavior remains **Needs manual check**.

## Q. Insight Center Decision Trend

- Implemented the approval/rejection trend as a JavaFX line chart with date labels, date/evaluation axes, a visible legend, and the application's real outcome labels: `Eligible` and `Rejected`.
- Aggregates saved evaluation decisions by local date over the last 30 days. Only known Boolean decisions with valid timestamps are included; pending/unknown outcomes, missing or malformed timestamps, and dates outside the range are excluded.
- Handles legacy named outcomes using the recognized domain aliases and preserves unrecognized outcomes as unavailable instead of treating them as rejections. Offset and BSON date timestamps are normalized for local-date grouping.
- Dashboard evaluation loading and chart aggregation remain in the existing background task; chart updates happen on JavaFX task success. No date-range selector or synthetic series/data was added.
- Updated the existing evaluation collection validator to accept a nullable `overallDecision`, allowing unknown legacy outcomes to remain unknown through mapper persistence. This does not add a collection or required field.
- Added tests for daily grouping, range filtering, omission of unknown/undated decisions, legacy outcome mapping, supported timestamp shapes, conflicting/unknown values, and validator compatibility.
- Verification: `mvn clean test` **BUILD SUCCESS** — 74 tests, 0 failures, 0 errors, 0 skipped; 94 production sources and 21 test sources compiled. MongoDB-backed applicant and migration integration tests ran. Existing JavaFX model/module-path, MongoDB logging/native-access, and Applicants unchecked-operation warnings remain. `git diff --check` passed.
- The JavaFX chart has not been manually launched/rendered; visual verification remains **Needs manual check**.

## R. Insight Center Policy Health Monitor

- Added a responsive Policy Health section to the existing Insight Center with compact lifecycle summary cards, a recent policy list, restrained semantic status badges, and a View all policies action.
- Loads the policy records and their saved versions through the existing `PolicyRepository.findAll()` on the dashboard's background task. The view shows up to six most recently version-updated policies; clicking a row opens that policy in the existing Policy Studio details view.
- Derives Active from non-archived policies with saved versions and Draft from non-archived policies without saved versions. Versioned counts any policy with saved versions; archived records remain visible with their archived status.
- Last updated is derived from saved version timestamps; missing timestamps are shown as unavailable, and policies without versions show no current version/date. No policy health score or validation findings are synthesized.
- The model has no validation/attention result, so Needs Attention is explicitly unavailable rather than reported as zero. Lifecycle summary cards are informational because the Policies screen has no corresponding filtered views.
- Added loading skeletons, an explicit no-policies empty state, and the existing retryable error treatment. Policy row selection resolves the clicked policy to the repository-loaded instance before displaying details.
- Verification: `mvn clean test` **BUILD SUCCESS** — 74 tests, 0 failures, 0 errors, 0 skipped; all 94 production sources and 21 test sources compiled. `git diff --check` passed. Existing Maven JavaFX module-path, MongoDB logging/native-access, and Applicants unchecked-operation warnings remain.
- JavaFX rendering and row navigation have not been manually exercised; visual/interaction verification remains **Needs manual check**.

## S. Insight Center Decision Pipeline

- Added the existing Decision Pipeline card to the responsive dashboard grid and refresh it when the dashboard is shown, when the selected program/policy/version changes, when applicant state changes, and when saved results change.
- The pipeline scope uses the selected policy and version plus the dashboard's optional program filter; evaluation-run and decision filters do not alter it because outcome counts use each applicant's latest saved result across runs.
- The pure `PipelineCalculator` reports applicant totals in the selected program scope (or the full repository when no program is selected), applicants awaiting a stored result, distinct applicants with a result, and latest known approved/rejected outcomes. It excludes other policy versions, supports legacy policy-version IDs, and never treats missing results as rejections. Ambiguous latest outcomes make completed/approved/rejected counts unavailable rather than violating their count invariant.
- Failed/incomplete is explicitly unavailable because `EvaluationResult` has no evaluation status/error field. Zero-condition versions show a warning and suppress metrics. Attention rows contain only applicant display name, policy, and awaiting badge; no personal contact, score, or timestamp fields are available in the applicant model.
- Summary and attention data load in separate background Tasks with skeleton, retryable error/toast, offline, no-policy, no-applicant, and no-evaluation states. Only the total-applicants card navigates to an existing unfiltered Applicants view; other stages explain that matching filtered destinations do not exist. Applicant rows use existing Applicants navigation/selection.
- Added pure calculator coverage for empty cohorts, no results, approved/rejected/mixed outcomes, distinct latest-run counts, policy-version isolation, legacy version IDs, misconfigured versions, unknown outcomes, and the approved + rejected = completed / awaiting = total - completed invariants. Added in-memory repository coverage for locating legacy results by policy-version ID.
- Observed test fixture cross-check: expected and calculated values were Total 3, Awaiting 1, Completed 2, Approved 1, Rejected 1. No configured MongoDB/demo records were read or seeded for this verification.
- Verification: `mvn clean compile` **BUILD SUCCESS** — 95 production sources compiled. `mvn clean test` **BUILD SUCCESS** — 85 tests, 0 failures, 0 errors, 3 Atlas-gated applicant repository tests skipped. `git diff --check` passed. Existing JavaFX module-path/effective-model and Applicants unchecked-operation warnings remain.
- No JavaFX window or live Atlas-backed pipeline was exercised. Stage values against persisted/demo data, row/card navigation, loading/empty/error/offline states, keyboard interaction, and 1100x700 / 1600x900 rendering remain **Needs manual check**.

## T. Programs & Applicants — Stage 1

- Updated the existing Program Manager page header and added a three-card KPI strip for Total Programs, Total Applicants, and Active Programs. The page still uses the separate `ProgramConfiguration` domain model; these are not Policy records.
- Total Programs counts configured program records excluding built-in `template-*` records and includes archived programs. Total Applicants counts applicant documents, independently of evaluations. Active Programs counts configured records that are not archived and excludes built-in templates. Program counts use repository queries; applicant counts use MongoDB `countDocuments()`. When MongoDB is offline or a load fails, affected KPI values explicitly show `Unavailable` rather than implying zero.
- KPI values load in a background JavaFX Task with an explicit failure log/toast and unavailable values on error. KPI cards have definition tooltips and navigate/filter only to existing destinations supported by the screen.
- Added a Programs/Applicants segmented navigation control using the existing Applicants route, program search, status and template filters, removable filter chips, alphabetical sort, result count, and Clear filters. Existing program creation and field/schema management behavior remains in place.
- Added repository-level active-program counts for MongoDB and in-memory stores and service coverage that confirms archive changes the active count without changing total count. Extracted program search/status/type matching and name sorting into a pure-Java criteria helper with unit tests.
- Verification: `mvn clean compile` **BUILD SUCCESS** — 96 production sources compiled. `mvn clean test` **BUILD SUCCESS** — 87 tests, 0 failures, 0 errors, 0 skipped. Mongo-dependent repository/migration tests ran in this environment. `git diff --check` passed.
- This is **Stage 1 only**; the JavaFX app was not launched and no live data values or screen interactions were observed. KPI values against hand-counted live data, tab navigation, filters/chips/sort, 1100x700 and 1600x900 layout, offline/error states, and keyboard/focus behavior remain **Needs manual check**. Programs card/table views, detail drawer, applicant detail drawer, and additional UI polish remain for later stages.

## U. MySQL-to-MongoDB Synchronization

- Repository tracing found no MySQL DDL or trigger setup script even though Java expects `ruleweaver_sql.synced_applicants` and `sync_outbox`. The example duplicate-key insert itself cannot fire `AFTER INSERT`; an existing row may predate trigger installation or have been inserted while MongoDB-origin suppression was active. Live MySQL trigger state could not be queried from this environment.
- Added a rerunnable SQL installer that creates the expected tables only if absent and recreates the three named triggers. It queues insert/update as `UPSERT`, delete as `DELETE`, and skips trigger writes while `@ruleweaver_sync_origin = 'mongo'`. It does not drop or alter tables or applicant data.
- Added optional `MYSQL_URL` configuration (default remains `localhost:3306/ruleweaver_sql`) and aligned the MySQL connectivity diagnostic with the application's connection factory. Connection strings are redacted from application synchronization diagnostics.
- Improved worker and change-stream diagnostics. Failed outbox events remain pending; processing status remains updated only after MongoDB returns successfully. Sync operations continue on background threads.
- Corrected loop prevention so later MongoDB edits to a document that retains the worker's `_sync_origin = 'mysql'` marker are not accidentally ignored. Added unit coverage for this behavior and connection-string redaction.
- Correctly unwraps BSON string IDs in MongoDB delete change events; otherwise string-keyed applicants could be missed by the MySQL soft-delete update.
- Documented SQL setup, safe one-row enqueue for pre-existing applicants, five-part repeatable verification, and live-server trigger inspection in the README.
- Validation: final `mvn clean test` **BUILD SUCCESS** — 99 tests, 0 failures, 0 errors, 0 skipped; `git diff --check` passed. Existing JavaFX effective-model and unchecked-operation warnings remain. MySQL CLI/client and live MySQL/Atlas synchronization were unavailable, so trigger installation and A–E live synchronization checks remain manual; no live Atlas success is claimed.
