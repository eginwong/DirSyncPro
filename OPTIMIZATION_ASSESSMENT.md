# DirSyncPro sync algorithm assessment

## Scope
This review focuses on the sync analysis path, especially the traversal and comparison logic in [src/main/java/job/Job.java](src/main/java/job/Job.java), the queue/filter logic in [src/main/java/sync/SyncQ.java](src/main/java/sync/SyncQ.java), and the helper utilities in [src/main/java/tools/FileLister.java](src/main/java/tools/FileLister.java).

## Executive summary
The current implementation is correct for a straightforward file-by-file mirror, but there are a few clear opportunities to reduce repeated work and make the sync algorithm more efficient without changing behavior.

The biggest optimization opportunities are:
- avoiding repeated full directory scans when one sync pass can answer both comparison and deletion questions,
- reducing repeated comparison work in the hottest file-pair path,
- avoiding repeated filter checks during traversal,
- and reducing repeated queue filtering in the UI layer.

This assessment is organized by finding, with the corresponding experiments listed directly under each finding.

## Progress at a glance
- ✅ Kept: paired-path caching and the identical-size/date early exit in [src/main/java/job/Job.java](src/main/java/job/Job.java).
- ❎ Rejected: local filter boolean hoisting, filter-result memoization, and per-analysis file metadata caching.
- ⏳ Not attempted: index-based traversal consolidation, lazy queue filtering, comparison-decision refactoring, and legacy utility cleanup.

There is no current unintentional revert of the successful paired-path optimization; it is present in the current source and remains the implemented optimization.

## 1. Finding: the analyzer does multiple full tree walks for a single job
What it is:
In [src/main/java/job/Job.java](src/main/java/job/Job.java), `generateSyncQ()` performs a deletion walk and a comparison walk. In bidirectional modes, `analyze()` calls `generateSyncQ()` twice, once for each direction.

Why it matters:
- every file and directory is visited multiple times;
- file metadata is repeatedly queried;
- the queue is repeatedly populated with items that may already be known to be redundant or identical;
- the cost compounds as tree size grows.

Experiments attempted for this finding:
- ❎ Rejected: a pair-aware in-memory index for unidirectional mirror modes. Benchmark averages were 82/75/176 ms for 100/500/1500 files versus 77/77/177 ms baseline; the queue remained unchanged and the small large-tree gain did not justify the implementation complexity, so it was reverted.
- Not attempted yet: build a production index that replaces repeated deletion and comparison work without changing filter or conflict semantics.
- Kept separately: paired-path caching and the identical-size/date early exit belong to the per-file hot path under Finding 3, not to traversal consolidation.

Status: highest-priority optimization target.

## 2. ❎ Finding: filter checks are repeated on every file and directory
What it is:
In the nested visitor class in [src/main/java/job/Job.java](src/main/java/job/Job.java), `visitFile()` and `preVisitDirectory()` repeatedly call:
- `filters.matchesAnyIncludeFilter(...)`
- `filters.matchesAnyExcludeFilter(...)`

Why it matters:
- filter logic is evaluated for every visited path,
- the same pattern checks happen repeatedly even in the default wildcard case,
- this adds overhead in the walk even if it is not the largest cost.

Experiments attempted for this finding:
- ❎ Rejected and benchmarked: a local boolean hoist before `analyzeFilePair()` avoided duplicate filter evaluations but did not produce a consistent win.
- ❎ Rejected and benchmarked: per-path memoization for include/exclude decisions did not produce a consistent medium/large-workload win.
- ❎ Added temporarily, then reverted without benchmark evidence: a `FilterSet` include/exclude cache. The follow-up benchmark run stalled, so this implementation was not retained or counted as measured progress.

Status: rejected as a primary optimization target.

## 3. ✅ Finding: path remapping and duplicate no-op comparisons add avoidable overhead
What it is:
The per-file logic in `analyzeFilePair()` rebuilds paired paths for every visited file and continues into deeper comparison logic even when two files are obviously identical.

Why it matters:
- repeated absolute-path rebuilding adds overhead in the hottest loop;
- same-size/same-date no-op cases are common enough to matter;
- avoiding unnecessary file-pair processing helps especially in larger trees.

Experiments attempted for this finding:
- Kept: paired-path caching so the relative-path calculation is reused during a single analysis pass.
- Kept: early return for identical files in the default compare mode when size and timestamp match.
- Result: benchmark improved materially, especially in the larger scenarios: average runtime changed from 405/580/1319 ms to 77/77/177 ms for 100/500/1500 files, respectively.
- Related rejected experiment: per-analysis caching of basic file metadata. It regressed the 1500-file case from 177 ms to 570 ms and was reverted.

Status: paired-path and no-op optimizations are kept; metadata caching is rejected.

## 4. Finding: queue filtering is secondary to the analysis bottleneck
What it is:
The queue logic in [src/main/java/sync/SyncQ.java](src/main/java/sync/SyncQ.java) rebuilds filtered lists via `viewFilter()` and `syncFilter()` by iterating over the full queue each time.

Why it matters:
- if the UI toggles filters often, the same queue is scanned repeatedly;
- this is more of a view-layer cost than the main sync-analysis bottleneck;
- it is still worth improving, but not before the larger traversal cost is addressed.

Experiments attempted for this finding:
- Kept for correctness: lazy dirty-state invalidation for view and sync filtered lists, with rebuilds triggered after add/delete/status/append/filter-mode changes.
- Added: focused `SyncQ` add/delete/view regressions reproducing stale cached-list behavior.
- Benchmark impact: the sync analyzer showed no consistent speedup; the harness does not directly measure UI queue filtering. The queue remained stable at 134/660/1952 entries.
- Not attempted yet: bucketed lists or delta-based category buckets.

Status: cache correctness improvement kept; category bucketing remains unattempted.

## 5. Finding: the comparison decision tree is long and mode-heavy
What it is:
`analyzeSyncPairStatus()` in [src/main/java/job/Job.java](src/main/java/job/Job.java) contains a broad set of branches for different sync modes and compare modes.

Why it matters:
- the decision tree is hard to reason about and profile;
- it mixes business rules, comparison logic, and file metadata reads in one long method;
- that makes deeper optimization harder to do safely.

Experiments attempted for this finding:
- ❎ Rejected: caching basic file metadata within a single `Job.analyze()` pass, tested separately from the traversal experiment. It passed the full test suite but benchmarked worse at all three dataset sizes and was reverted.
- ❎ Rejected: direct buffered byte comparison in place of CRC equality. A dedicated 1 MB benchmark measured 3 ms equal/4 ms unequal versus 0 ms for CRC; it was reverted, though the real-file behavior regression remains.
- Not attempted yet: split the status logic into helpers such as `isNewFile`, `isDeletedFile`, `isModifiedFile`, `isLargerFile`, and `isConflict`.
- Not attempted yet: keep cheap checks first and restrict checksum or metadata comparison to the cases where they are truly needed.

Status: metadata caching is rejected; decision-tree refactoring remains an unattempted follow-up.

## 6. Finding: legacy utility code is not the main performance issue
What it is:
[src/main/java/tools/FileLister.java](src/main/java/tools/FileLister.java) is a recursive file enumerator with a `main` method, but it does not appear to be part of the core sync loop or central optimization path.

Why it matters:
- it adds maintenance burden;
- it suggests older implementation patterns;
- it is easy to confuse with the real sync algorithm when reviewing the codebase.

Experiments attempted for this finding:
- Completed: confirmed there were no workspace references to `FileLister` and removed the standalone demo class from `src/main/java/tools/FileLister.java`.

Status: completed cleanup; no sync-path behavior or performance impact.

## Experiment log summary
Status legend:
- Kept: implemented and benchmarked successfully
- Rejected: implemented and benchmarked, then reverted
- Not attempted yet: identified but not implemented

1. Kept: paired-path cache + identical-file fast path
2. ❎ Rejected: local filter boolean hoist
3. ❎ Rejected: per-path filter memoization
4. ❎ Rejected: per-analysis basic file metadata cache
5. ❎ Reverted without usable benchmark evidence: transient `FilterSet` include/exclude cache
6. ❎ Rejected: pair-aware mirror traversal index; effectively flat benchmark
7. ❎ Rejected: direct-byte checksum comparison; slower than CRC in dedicated benchmark
8. Kept for correctness: lazy SyncQ filtered-list invalidation
9. Not attempted yet: production traversal-index consolidation with stronger risk controls
10. Not attempted yet: bucketed queue views by status
11. Not attempted yet: refactor comparison decision tree into smaller helpers
12. Completed: removed unreferenced `FileLister` standalone demo utility

## Recommendation
The highest remaining ROI is still avoiding repeated analysis work in the sync hot path, particularly traversal consolidation. The first index-based mirror experiment was rejected because the benchmark improvement was effectively flat. The path-remapping optimization remains kept; queued filtering and comparison refactoring should remain lower priority until a traversal design provides a measurable gain.

This keeps the assessment aligned with the actual evidence and avoids mixing phases, experiments, and findings in ways that do not reflect the work performed.
