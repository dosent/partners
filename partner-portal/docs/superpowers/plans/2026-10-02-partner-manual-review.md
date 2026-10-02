# Partner Manual Review Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ensure every newly submitted or resubmitted partner application is routed to a human by starting in `MANUAL_REVIEW`.

**Architecture:** Keep the rule in the `partner` domain aggregate so every caller gets the same behavior. Reuse existing status history and persistence mapping; do not add a registry adapter or change schema.

**Tech Stack:** Java 21, Spring Boot 3, JPA/Liquibase (existing schema only).

**Spec:** `openspec/changes/partner-requisites-verification/specs/public-registration-access/spec.md` and `openspec/changes/partner-requisites-verification/design.md`.

## Global Constraints

- Every new or resubmitted application starts at `MANUAL_REVIEW`.
- Do not connect tax registries in this change.
- Do not automatically approve or reject an application.
- Preserve immutable application revisions and status history.
- Do not log full requisites or personal data.
- Do not add database schema changes unless implementation demonstrates they are required.

## File Structure

- Modify `backend/src/main/java/com/simbirsoft/partners/partner/domain/PartnerApplication.java`: set initial and resubmission status/history to `MANUAL_REVIEW`.
- Modify `backend/src/test/java/com/simbirsoft/partners/partner/domain/PartnerApplicationTest.java`: align existing expectations for submission and resubmission with the approved rule.
- Modify `backend/src/test/java/com/simbirsoft/partners/partner/infrastructure/PartnerApplicationRecordTest.java`: align expected persisted status history.
- Inspect `backend/src/main/java/com/simbirsoft/partners/partner/infrastructure/PartnerApplicationRecord.java` and `PartnerApplicationStore.java`; keep their mapping unchanged if status/history already round-trip without special cases.
- Update `openspec/changes/partner-requisites-verification/tasks.md` when the behavior is implemented.

## Review Focus

- New submissions must start in `MANUAL_REVIEW`; the existing assertion in `PartnerApplicationTest` pins this.
- Resubmissions after `CHANGES_REQUESTED` or `REJECTED` must also start in `MANUAL_REVIEW`; existing resubmission and record round-trip assertions pin this.
- A staff decision from `MANUAL_REVIEW` must still transition only to allowed terminal/request-changes statuses; existing transition coverage must remain intact.
- Existing stored `PENDING_REVIEW` applications must remain loadable; no enum removal or schema rewrite.
- No registration caller may silently reset the status to `PENDING_REVIEW`; inspect current references to `PartnerApplication.submit` and `resubmit` before finishing.

---

### Task 1: Route all submissions to manual review

**Files:**
- Modify: `backend/src/main/java/com/simbirsoft/partners/partner/domain/PartnerApplication.java`
- Modify: `backend/src/test/java/com/simbirsoft/partners/partner/domain/PartnerApplicationTest.java`
- Modify: `backend/src/test/java/com/simbirsoft/partners/partner/infrastructure/PartnerApplicationRecordTest.java`
- Inspect: `backend/src/main/java/com/simbirsoft/partners/partner/infrastructure/PartnerApplicationRecord.java`
- Inspect: `backend/src/main/java/com/simbirsoft/partners/partner/infrastructure/PartnerApplicationStore.java`

**Interfaces:**
- Consumes: Existing `PartnerApplicationStatus.MANUAL_REVIEW`, `PartnerApplication.submit(...)`, `PartnerApplication.resubmit(...)`, and status-history persistence.
- Produces: Both submission methods return an aggregate whose current status and first/next history event are `MANUAL_REVIEW`.

- [x] Change `PartnerApplication.submit(...)` to create its first status event as `MANUAL_REVIEW` with actor `PARTNER`.
- [x] Change `PartnerApplication.resubmit(...)` to append a `MANUAL_REVIEW` event for the new immutable revision.
- [x] Update only the existing assertions that expect a new submission or resubmission to be `PENDING_REVIEW`; preserve assertions for legacy restored `PENDING_REVIEW` aggregates and staff transitions.
- [x] Inspect record conversion and persistence round-trip; change them only if they contain status-specific assumptions that prevent `MANUAL_REVIEW` from being stored and restored.
- [x] Inspect all production references to `submit` and `resubmit`; confirm none overwrites the aggregate status with `PENDING_REVIEW`.
- [x] Mark the corresponding implementation items in `openspec/changes/partner-requisites-verification/tasks.md` complete after the changes are reviewable.

### Task 2: Review the resulting change

**Files:**
- Review: `openspec/changes/partner-requisites-verification/proposal.md`
- Review: `openspec/changes/partner-requisites-verification/design.md`
- Review: `openspec/changes/partner-requisites-verification/specs/public-registration-access/spec.md`
- Review: `openspec/changes/partner-requisites-verification/tasks.md`
- Review: `openspec/changes/partner-registration/proposal.md`
- Review: `openspec/changes/partner-registration/.openspec.yaml`
- Review: `openspec/changes/partner-registration/design.md`
- Review: `openspec/changes/partner-registration/specs/partner-registration/spec.md`
- Review: `openspec/changes/partner-registration/specs/partner-application-review/spec.md`

- [ ] Check that all active registration requirements consistently say every submission receives human review.
- [ ] Check the final diff for unintended edits and whitespace errors.
- [ ] Record which automated checks were not run; do not report them as passing.
