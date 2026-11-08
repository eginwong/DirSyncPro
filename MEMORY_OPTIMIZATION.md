# Memory Optimization Backlog

## Goal

Reduce heap use in large directory synchronization jobs and prevent out-of-memory
errors without relying only on larger `-Xmx` values.

The native packages currently set `-Xmx1g`. A 1 GB heap is useful as a
baseline, but memory should scale with actual retained application data rather
than simply increasing the cap.

## Current findings

### Traversal memory

The small-tree optimization for `ABMirror` and `BAMirror` retains a relative-path
index. Large mirror trees use streaming traversal:

- The index is enabled below 10,000 directory/file entries.
- At or above the threshold, mirror modes do not retain the complete index.
- This is implemented in `src/main/java/job/Job.java`.

The traversal-index work is already addressing directory enumeration memory. It
should not be expanded without new profiling and controlled benchmarks.

### Operation queue retention

Each changed or deleted operation is retained as a `SyncPair` in the global
`SyncQ`. A benchmark retained 129,948 queue entries for a 100,000-file source
and 100,000-file target scenario.

The queue is the primary scaling concern for jobs containing many differing
entries. Each pair currently retains:

- Two `File` objects.
- Two dates and two sizes.
- Multiple existence/type flags.
- A status, job reference, and icon.
- Additional materialized queue references for active filters.

Relevant implementation:

- `src/main/java/sync/SyncPair.java`
- `src/main/java/sync/SyncQ.java`
- `src/main/java/sync/Sync.java`

### Queue is reset between analyses

`Sync.analyze()` creates a new `SyncQ` before each analysis run. This makes a
simple cross-run accumulation leak unlikely, but stale references should still
be checked in heap dumps because UI models and filter queues can retain the
previous or current queue.

## Optimization priorities

### 1. Lazily resolve SyncPair icons

`SyncPair` currently calls `FileSystemView.getSystemIcon()` while constructing
every operation.

Work:

- Remove the eager icon lookup from the pair constructor: completed.
- Resolve icons lazily only when rendered: completed.
- Prefer a small shared icon cache over one icon reference per pair: completed
  with a bounded cache of 128 extension/directory entries.
- Consider rendering generic file/directory icons instead of unique system
  icons for large queues if behavior permits.

Verification:

- Confirm queue construction heap and icon allocation decrease.
- Confirm UI icons remain correct in table and tree views.

### 2. Remove unnecessary job references

Determine whether every `SyncPair` needs to retain its complete `Job` reference.
Some operations may be able to retain only the immutable synchronization data
used by execution.

Work:

- Measure how often and where `SyncPair.getJob()` is required.
- Replace the job reference with smaller fields only if removal is safe.
- Check whether cloned jobs preserve required behavior.

Verification:

- Run sync, copy, delete, conflict, and post-analysis tests.
- Confirm no scheduler or GUI path depends on the pair job reference.

### 3. Avoid duplicated filtered queue lists

`SyncQ` can create separate view-filtered and sync-filtered lists containing
references to the same `SyncPair` objects.

Work:

- Keep the base queue as the source of truth.
- Measure the frequency and benefit of materialized filter lists.
- If retained lists remain useful, lazily create and cache them and release them
  when filters or the base queue change.
- Prefer iterator/index-based views when the benchmark shows no material benefit.

Verification:

- Test filter changes, row selection, synchronization of filtered operations,
  and queue additions/removals.
- Include UI filtering workloads in benchmarks.

### 4. Compact queue representations

If heap profiling confirms that `SyncPair` objects remain the main retention,
evaluate more compact operation storage.

Potential work:

- Store only one path representation when source/destination exists in a pair.
- Remove redundant flags derivable from status.
- Use interned/shared enum/status/job data where appropriate.
- Measure array, primitive, or compact record storage against `ArrayList` and
  object overhead.

Verification:

- Avoid premature storage changes that add synchronization or UI complexity.
- Preserve behavior and benchmark at 10,000, 50,000, and 100,000 entries.

## Heap limit strategy

The 512 MB legacy setting should be treated as historical behavior, not the
current packaged baseline.

- Keep the current native 1 GB maximum as a mitigation.
- Consider 2 GB only if heap profiling demonstrates that operation retention
  approaches or exceeds the 1 GB heap for supported workloads.
- Prefer code reductions in retained queue size before raising the heap further.
- Do not set an unlimited or machine-dependent value in packaging.

For direct JAR launches, 1 GB can be tested with:

```text
java -Xmx1G -jar dirsyncpro.jar
```

## Profiling plan

Before making invasive changes, capture evidence from the failing workload:

1. Reproduce the out-of-memory failure on a known directory set.
2. Enable a Java heap dump at out-of-memory:

   ```text
   -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/path/to/dumps
   ```

3. If supported, record GC behavior:

   ```text
   -Xlog:gc*:file=/path/to/gc.log:time,uptime,level,tags
   ```

4. In the heap dump, rank live retention for:
   - `SyncPair`
   - `SyncQ`
   - `File`
   - `Icon` implementations
   - `ArrayList`
   - filtered queue lists
   - traversal index maps
   - cloned `Job` instances

5. Confirm whether heap growth occurs during one analysis or accumulates across
   repeated analyses.

## Validation

Each optimization should be changed independently and measured with:

- The existing Maven test suite.
- The benchmark scenarios in `BENCHMARKING.md`.
- 10,000, 50,000, and 100,000-entry datasets.
- Peak heap measurements where possible.
- Queue size and runtime before/after comparisons.
- A small correctness test and an actual large synchronization job.

Do not mark an optimization complete solely because runtime improves. It must
also reduce retained heap or demonstrably prevent memory scaling without
introducing regressions.
