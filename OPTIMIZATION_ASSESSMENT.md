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
- ✅ Kept: paired-path caching, identical-size/date early exits, and cheap gate ordering before checksum or metadata comparison in [src/main/java/job/Job.java](src/main/java/job/Job.java).
- ❎ Rejected: local filter boolean hoisting, filter-result memoization, per-analysis metadata caching, and direct buffered-byte replacement for CRC comparison.
- ✅ Kept: production relative-path indexes for unidirectional mirror analysis.
- ❎ Rejected: relative-path traversal expansion into bidirectional/custom modes.
- ✅ Kept: lazy queue filtering invalidation.
- ⏳ Remaining: bucketed queue views and comparison-decision refactoring.

There is no current unintentional revert of the successful paired-path optimization; it is present in the current source and remains the implemented optimization.

## ✅ 1. Finding: the analyzer does multiple full tree walks for a single job
What it is:
In [src/main/java/job/Job.java](src/main/java/job/Job.java), `generateSyncQ()` performs a deletion walk and a comparison walk. In bidirectional modes, `analyze()` calls `generateSyncQ()` twice, once for each direction.

Why it matters:
- every file and directory is visited multiple times;
- file metadata is repeatedly queried;
- the queue is repeatedly populated with items that may already be known to be redundant or identical;
- the cost compounds as tree size grows.

Experiments attempted for this finding:
- ❎ Earlier pair-aware mirror experiment rejected: 82/75/176 ms versus 77/77/177 ms showed no material repeatable gain.
- ✅ Kept production index: `ABMirror` and `BAMirror` now build one relative-path index per root and union the results instead of running separate target deletion and source copy visitor walks.
- Controlled benchmark: 103/94/238 ms without the production index versus 86/81/191 ms with it for 100/500/1500 files. Queue sizes remained 134/660/1952.
- Added mixed forward-mirror and reverse-mirror regressions. The full suite passes, including exclusion/copy semantics inherited from the existing analyzer.

Status: production unidirectional mirror optimization kept. The bidirectional/custom traversal expansion was rejected after the benchmark measured ~102/101/232 ms versus the 77/77/177 ms baseline, so this implementation must not be reapplied without new controlled benchmark evidence.

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
- ❎ Rejected after implementation: bucketed lists by copy category; controlled benchmark showed mixed results, including a 1500-file regression, and the implementation was reverted.

Status: cache correctness improvement kept; category bucketing is rejected until filtering workload evidence is collected from the UI.

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
- ❎ Rejected: split the status predicates into helpers; the 19-test suite passed, but benchmark averages rose to 178/218/365 ms and were not kept.
- ✅ Kept: cheap gates already precede expensive checks: CRC content comparison is reached only for equal-size files, and metadata comparison is reached only when the modification dates already match.
- ❌ Rejected: a metadata-cache variant and direct buffered-byte replacement for CRC comparison were separately benchmarked and reverted; neither provided a reliable improvement, and direct bytes were slower in the dedicated 1 MB benchmark.

Status: metadata caching, direct-byte comparison, and helper-based comparison-predicate refactoring are rejected; the helper experiment passed tests but benchmarked materially slower and was reverted.

## ✅ 6. Finding: legacy utility code is not the main performance issue
What it is:
`FileLister` was a standalone recursive file enumerator with a `main` method, but it was not part of the core sync loop or central optimization path.

Why it matters:
- it adds maintenance burden;
- it suggests older implementation patterns;
- it is easy to confuse with the real sync algorithm when reviewing the codebase.

Experiments attempted for this finding:
- Completed: confirmed there were no workspace references to `FileLister` and removed the standalone demo class from `src/main/java/tools/FileLister.java`.

Status: completed cleanup; no sync-path behavior or performance impact.

## ✅ 7. Finding: traversal indexes are not efficient for large mirror trees
What it is:
For `ABMirror` and `BAMirror`, the production implementation builds a relative-path index for both roots. This helped the controlled 100/500/1500-file benchmark, but retaining every path, wrapper object, and relative-path string is unnecessary when the tree is large.

Why it matters:
- every entry consumes heap while the index remains live;
- HashMap construction and iteration add CPU and allocation work;
- filesystem metadata cannot skip the directory enumeration;
- queue and object allocation become dominant at very large scales.

Experiment:
- Controlled local benchmark with 100,000 source files and 100,000 target files under identical timestamps produced approximately 8.51 seconds for the current implementation and 8.78 seconds for version 1.53.
- A 100,000-file modified-tree run produced approximately 10.70 seconds current versus 10.85 seconds for version 1.53.
- The updated benchmark runner measured the threshold implementation at 1,471 ms for 10,000 files, 8,463 ms for 50,000 files, and 18,492 ms for 100,000 files; all three scenarios selected streaming traversal.
- These results were effectively within noise and showed no repeatable large-tree advantage for retaining both traversal indexes.

Implementation:
- Mirror modes now sample both roots up to a bounded threshold of 10,000 directory/file entries.
- Below the threshold, `ABMirror` and `BAMirror` use the relative-path index.
- At or above the threshold, mirror modes use the streaming `generateSyncQ()` visitor, which does not retain the complete entry index.
- Bidirectional and custom modes remain unchanged.

Status: kept. The threshold avoids the large-tree index allocation while preserving the small-tree optimization; benchmark coverage now includes 10,000, 50,000, and 100,000-file scenarios.

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
8. ✅ Kept for correctness: lazy SyncQ filtered-list invalidation
9. ✅ Kept: production traversal index for `ABMirror` and `BAMirror`; controlled benchmark improved 100/500/1500 averages to 86/81/191 ms
10. ❎ Rejected: indexed traversal expansion for `ABCustom`, `BACustom`, `BICustom`, and `BIMirror`; benchmarked at 102/101/232 ms versus the 77/77/177 ms baseline and reverted
11. ❎ Rejected: bucketed queue views by status; controlled benchmark was 69/81/187 ms versus 90/87/180 ms, with mixed gains and a 1500-file regression
12. ❎ Rejected: comparison-predicate helper extraction; 19 tests passed, benchmark averaged 178/218/365 ms and the change was reverted
13. ✅ Completed: removed unreferenced `FileLister` standalone demo utility
14. ✅ Kept: thresholded mirror traversal; relative-path indexes remain enabled below 10,000 entries and large mirrors use streaming analysis

## Recommendation
The unidirectional mirror traversal index remains useful for small and medium trees, but large mirror trees use the streaming visitor to avoid retaining a full index. Bucketed queue views are rejected: the current benchmark showed mixed gains (69/81 ms at 100/500 files) but a 1500-file regression (187 ms versus 180 ms) and does not exercise UI filter workloads. The bidirectional/custom traversal expansion is also rejected. Additional optimization should start with a profiler and a 100,000+ file benchmark, because at large scales filesystem enumeration, queue allocation, and GC are likely to dominate the comparison micro-optimizations.
