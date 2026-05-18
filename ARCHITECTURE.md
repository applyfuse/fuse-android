# FUSE Architecture

**The FUSE architecture pattern is documented in one canonical place:**

### → https://github.com/applyfuse/fuse-docs/blob/main/ARCHITECTURE.md

This repo does **not** keep its own copy. A duplicated architecture
doc across `fuse-ios`, `fuse-android`, and `fuse-docs` drifts — the
three copies silently disagree over time. So the pattern lives in
exactly one file, in `fuse-docs`, and every repo points here.

That document covers, platform-neutrally, with both Swift and Kotlin
examples:

- The mental model and the 6 rules
- Platform mapping (iOS ↔ Android)
- How to add a new feature (8-step checklist)
- What-goes-where, effect patterns, testing strategy
- State design, common mistakes, scaling, architecture comparison

## Android-specific implementation context

Anything specific to *this* repo — the concrete `BaseViewModel<S,A>`,
Hilt wiring, Detekt/Kotlin conventions and the catalogued pitfalls,
the local-CI workflow, the coroutine-determinism notes — lives in
this repo, not in the canonical doc:

- **`CLAUDE.md`** — Android project context, conventions, pitfalls
- **`DATA_LAYER.md`** — transport / token store / refresh wiring
  (draft until Phase 2 lands those components — see `PHASE_2.md`)
- **`FUSE_PLAN.md`** — the 6-phase roadmap (shared goals, Android status)
- **`PHASE_*.md`** — the active phase's execution plan

## Editing the pattern

If you need to change the *pattern itself* (a rule, the mental
model, a cross-platform convention), edit
`fuse-docs/ARCHITECTURE.md` — the single source of truth — **never
here**. There is nothing to edit in this file except this pointer.
