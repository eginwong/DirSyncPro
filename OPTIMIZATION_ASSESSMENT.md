# DirSyncPro sync algorithm assessment

## Scope
This review focuses on the sync analysis path, especially the traversal and comparison logic in [src/main/java/job/Job.java](src/main/java/job/Job.java), the queue/filter logic in [src/main/java/sync/SyncQ.java](src/main/java/sync/SyncQ.java), and the helper utilities in [src/main/java/tools/FileLister.java](src/main/java/tools/FileLister.java).

## Executive summary
The current implementation is correct for a straightforward file-by-file mirror, but there are a few clear opportunities to reduce repeated work and make the sync algorithm more efficient without changing behavior.

The biggest optimization opportunities are:
- avoid repeating full directory scans when one sync pass can answer both the comparison and deletion questions;
- avoid re-evaluating filter logic for the same path repeatedly during a traversal;
- avoid repeatedly rebuilding filtered queue views on UI state changes; and
- reduce expensive per-file comparison work by doing the most expensive checks only after cheaper checks already indicate a difference.

## High-impact findings

### 1) The analyzer is doing multiple full tree walks for a single job
In [src/main/java/job/Job.java](src/main/java/job/Job.java), `generateSyncQ()` performs a deletion walk and a comparison walk. In bidirectional modes, `analyze()` calls `generateSyncQ()` twice, once for each direction.

That means the algorithm can perform up to:
- 2 full passes for a single-direction mirror; and
- 4 full passes for a bidirectional sync.

For large trees, this is the strongest candidate for optimization.

Why this matters:
- every file and directory is visited multiple times;
- file metadata is repeatedly queried;
- the queue is repeatedly populated with items that may already be known to be redundant or identical;
- the cost compounds as tree size grows.

Suggested improvement:
- build a single in-memory index of each side of the sync (relative path, file type, size, mod time, and normalized filter state);
- compare the two indexes once and derive both copy actions and deletion actions from that index;
- keep the current logic as a fallback if a full rewrite is too risky, but move toward a single-pass index-based algorithm as the main path.

### 2) Filter checks are repeated on every file and directory
In the nested visitor class in [src/main/java/job/Job.java](src/main/java/job/Job.java), `visitFile()` and `preVisitDirectory()` repeatedly call:
- `filters.matchesAnyIncludeFilter(...)`
- `filters.matchesAnyExcludeFilter(...)`

This is a repeated cost for every visited path, even if the filter result is stable for the same relative path or subtree.

This is especially worth optimizing because:
- filters are often evaluated with string/pattern matching against relative paths;
- the same directory tree can be traversed repeatedly during analysis;
- the cost is not just the pattern check itself; it also scales with the number of configured filters.

Suggested improvement:
- cache filter decisions by normalized relative path;
- cache subtree inclusion decisions for directories so excluded subtrees are pruned earlier;
- avoid re-evaluating filters against the same file more than once per analyze pass.

### 3) Sync queue filtering reconstructs lists repeatedly
The queue logic in [src/main/java/sync/SyncQ.java](src/main/java/sync/SyncQ.java) rebuilds filtered lists via `viewFilter()` and `syncFilter()` by iterating over the full queue each time.

This has a few problems:
- if the UI toggles filters often, the same queue is scanned repeatedly;
- it re-evaluates the full set of items even when only one category changed;
- filtering is O(n) and is called repeatedly, which makes the UI and analysis feel slower on large sync sets.

Suggested improvement:
- compute filtered lists lazily and only invalidate the cache when the underlying queue changes;
- avoid scanning the full queue to rebuild the view unless the queue actually changed;
- optionally maintain bucketed lists by status to remove repeated scan loops.

### 4) Expensive checks are triggered in a broad set of cases
In `analyzeSyncPairStatus()` in [src/main/java/job/Job.java](src/main/java/job/Job.java), the code always compares dates and sizes first, and then does more expensive operations when needed.

This part is mostly well-structured, but there are still opportunities to optimize the path for large files:
- checksum comparison is only done under `CompareFileContents`, but it is still a heavy operation and should be gated behind a quick pre-check that is as cheap as possible;
- metadata comparison is done only when the compare mode is metadata-aware, but it should be batched with a single pass where possible instead of repeated file reads across many entries;
- the algorithm currently handles many mode branches in one long method, which makes it harder to reason about where the cost is spent and to optimize selectively.

Suggested improvement:
- use early exits and cheap comparisons first;
- only do content or metadata comparisons when the date and size heuristics indicate a likely mismatch;
- split the logic into smaller status-specific helpers so expensive branches are easier to profile and optimize.

## Medium-impact cleanup areas

### 5) There is legacy utility code that looks unneeded in the hot path
[src/main/java/tools/FileLister.java](src/main/java/tools/FileLister.java) is a recursive file enumerator with a `main` method, but it does not appear to be part of the core sync loop or a central optimization strategy.

This is not necessarily a performance bug, but it is a good candidate for trimming if it is no longer used, because:
- it adds maintenance burden;
- it suggests older implementation patterns; and
- it is easy to confuse with the real sync algorithm when analyzing the codebase.

Suggested improvement:
- confirm whether it is still referenced anywhere; if not, remove it or move it to a test-only utility area.

### 6) Long decision tree in one method is harder to optimize safely
`analyzeSyncPairStatus()` is long and contains many mode-specific branches. This makes the code harder to profile and refactor because the business rules are mixed with traversal and comparison logic.

Suggested improvement:
- extract status classification into helper methods such as:
  - `isNewFile`
  - `isDeletedFile`
  - `isModifiedFile`
  - `isLargerFile`
  - `isConflict`
- preserve the current semantics first, then optimize each helper independently.

## Practical optimization plan

### Rejected experiment: filter-result micro-optimization
A first optimization pass attempted to reduce duplicate include/exclude filter checks inside the visitor by assigning local boolean values before triggering `analyzeFilePair()`. This did not produce a measurable net win under the synthetic benchmark and was therefore reverted.

Why it did not help:
- the benchmark cost is dominated by pair-analysis work once files are visited,
- the tree walk still performs the same number of expensive comparisons,
- caching a few filter calls does not reduce the larger cost of repeated metadata and status logic for each file.

This tells us the next change should target the deeper hot path instead of the filter call count alone.

### Experiment 2: reduce per-file path remapping overhead
The next experiment targets the expensive per-item path remapping inside `analyzeFilePair()`. The method previously rebuilt paired paths by substringing absolute paths for every visited file. The new approach resolves the relative path once and then appends it to the opposite root.

Why this is a realistic next step:
- it preserves the same sync semantics,
- it avoids repeated absolute-path string operations in the hottest loop,
- it reduces object churn and string work without altering file matching logic.

This change is intentionally smaller and safer than a full traversal rewrite, so it is a good candidate for the next benchmarked experiment.

### Phase 1: Measure and benchmark the current behavior
1. Add a benchmark harness around `Job.analyze()` with a synthetic directory tree of different sizes.
2. Capture timings for:
   - small tree,
   - medium tree,
   - large tree,
   - recursive vs. non-recursive,
   - several compare modes.
3. Record the total number of visited files and directories and compare to the expected work.

### Phase 2: Consolidate traversal work
1. Replace repeated full scans with a single job-level index for each side.
2. Build a map keyed by relative path.
3. Use that index to derive copy, delete, and conflict actions.
4. Keep the current tree visitor as a compatibility layer while benchmarking the new approach.

### Phase 3: Cache repeated filter decisions
1. Add a cache for include/exclude filter decisions by relative path.
2. Cache directory prune decisions so excluded subtrees are skipped immediately.
3. Store the filter outcome once per file/directory instead of recalculating during each pass.

### Phase 4: Reduce queue cost
1. Make `SyncQ` view/sync filters lazy and cacheable.
2. Add a delta-based invalidation model instead of rebuilding all filtered lists every time.
3. If necessary, maintain category buckets for `new`, `modified`, `larger`, `redundant`, and `conflict` entries.

### Phase 5: Protect expensive comparison work
1. Keep cheap checks first (size, date, type, existence).
2. Restrict checksum and metadata comparison to cases where the cheap checks already show probable differences.
3. Keep a consistent path for all compare modes so the algorithm is predictable and easier to test.

## Likely ROI order
1. Consolidate full-tree scans into one index-based pass: highest impact.
2. Cache filter decisions during traversal: high impact with low risk.
3. Make SyncQ filtering lazy: medium impact and straightforward improvement.
4. Refactor long status methods: medium impact, low-to-medium risk.
5. Remove unused legacy utility code: low impact, cleanup only.

## Recommendation
If the goal is to improve sync performance with minimal change risk, the best next step is to tackle the duplicate tree traversal first and then add filter caching. Those two changes address the most expensive repeated work in the current design and give the clearest return on investment.
