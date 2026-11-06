---
description: Critic reviews all uncommitted changes to the cards and the overview files
agent: critic
subtask: true
---
Review the uncommitted changes to features/ and feature-overview/. Check for contradictions between ledger entries, cards and OWNER_PRINCIPLES, repeats of rejected ideas, unresolved ripples, stale terms and vague wording.

Changed files:
!`git status --short features feature-overview`

Diff summary:
!`git diff --stat -- features feature-overview`
