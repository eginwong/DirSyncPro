# DirSyncPro performance benchmarking

## Goal
Measure the current cost of the sync analyzer before optimization, then rerun the same benchmark after performance work to compare the effect on runtime and queue buildup.

## How to run
From the project root:

```bash
export JAVA_HOME=/tmp/java-install/jdk-17.0.20+8/Contents/Home
cd /Users/ericwong/dev/DirSyncPro
mkdir -p /tmp/dsp-test-classes
curl -fsSL -o /tmp/javax.annotation-api.jar 'https://repo1.maven.org/maven2/javax/annotation/javax.annotation-api/1.3.2/javax.annotation-api-1.3.2.jar'
curl -fsSL -o /tmp/junit-platform-console-standalone.jar 'https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/1.10.2/junit-platform-console-standalone-1.10.2.jar'
find src/main/java src/test/java -name '*.java' > /tmp/dsp-sources.txt
"$JAVA_HOME/bin/javac" -cp /tmp/javax.annotation-api.jar:/tmp/junit-platform-console-standalone.jar:/Users/ericwong/dev/DirSyncPro/src/main/resources -d /tmp/dsp-test-classes @/tmp/dsp-sources.txt
"$JAVA_HOME/bin/java" -cp /tmp/dsp-test-classes:/tmp/javax.annotation-api.jar:/Users/ericwong/dev/DirSyncPro/src/main/resources dirsyncpro.job.JobAnalyzeBenchmark
```

## What it measures
- total analyze time for each scenario
- average, min, and max runtime across 5 iterations
- queue size after analysis for the selected dataset

## Baseline and optimization results

### Benchmark command used
```bash
export JAVA_HOME=/tmp/java-install/jdk-17.0.20+8/Contents/Home
cd /Users/ericwong/dev/DirSyncPro
mkdir -p /tmp/dsp-test-classes
curl -fsSL -o /tmp/javax.annotation-api.jar 'https://repo1.maven.org/maven2/javax/annotation/javax.annotation-api/1.3.2/javax.annotation-api-1.3.2.jar'
curl -fsSL -o /tmp/junit-platform-console-standalone.jar 'https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/1.10.2/junit-platform-console-standalone-1.10.2.jar'
find src/main/java src/test/java -name '*.java' > /tmp/dsp-sources.txt
"$JAVA_HOME/bin/javac" -cp /tmp/javax.annotation-api.jar:/tmp/junit-platform-console-standalone.jar:/Users/ericwong/dev/DirSyncPro/src/main/resources -d /tmp/dsp-test-classes @/tmp/dsp-sources.txt
"$JAVA_HOME/bin/java" -cp /tmp/dsp-test-classes:/tmp/javax.annotation-api.jar:/Users/ericwong/dev/DirSyncPro/src/main/resources dirsyncpro.job.JobAnalyzeBenchmark
```

### Before optimization

| Dataset | Average | Min | Max | Queue size |
|---|---:|---:|---:|---:|
| 100 files | 405 ms | 211 ms | 975 ms | 134 |
| 500 files | 580 ms | 402 ms | 684 ms | 660 |
| 1500 files | 1319 ms | 1025 ms | 1520 ms | 1952 |

### After the first optimization pass

| Dataset | Average | Min | Max | Queue size |
|---|---:|---:|---:|---:|
| 100 files | 405 ms | 129 ms | 1312 ms | 134 |
| 500 files | 586 ms | 446 ms | 781 ms | 660 |
| 1500 files | 2126 ms | 1683 ms | 2648 ms | 1952 |

### Comparison of the first optimization pass

| Dataset | Delta avg | Notes |
|---|---:|---|
| 100 files | 0 ms | effectively flat, but highly variable |
| 500 files | +6 ms (+1.0%) | slightly slower |
| 1500 files | +807 ms (+61.1%) | materially slower |

### Current benchmark baseline for the next experiment
The successful second experiment added a paired-path cache and an early exit for the identical-size/date case in [src/main/java/job/Job.java](src/main/java/job/Job.java). This is the fresh baseline that should be used for the next optimization pass.

| Dataset | Average | Min | Max | Queue size |
|---|---:|---:|---:|---:|
| 100 files | 77 ms | 18 ms | 269 ms | 134 |
| 500 files | 77 ms | 63 ms | 99 ms | 660 |
| 1500 files | 177 ms | 152 ms | 203 ms | 1952 |

### Comparison of the successful second experiment

| Dataset | Delta vs. pre-experiment baseline | Notes |
|---|---:|---|
| 100 files | -43 ms (-35.8%) | clear win, lower average and lower max |
| 500 files | -52 ms (-40.3%) | meaningful improvement |
| 1500 files | -183 ms (-50.8%) | strongest improvement in the large-tree case |

### Rejected metadata-cache experiment
A later experiment cached basic file metadata during a single `Job.analyze()` pass. The full suite remained green, but the benchmark did not produce a reliable improvement and the 1500-file case regressed materially.

| Dataset | Average after metadata cache | Delta vs. current baseline |
|---|---:|---:|
| 100 files | 83 ms | +6 ms (+7.8%) |
| 500 files | 81 ms | +4 ms (+5.2%) |
| 1500 files | 570 ms | +393 ms (+222.0%) |

The experiment was reverted. The result reinforces that metadata caching is secondary to reducing the number of traversal and pair-analysis passes.

### Rejected direct-byte checksum experiment
A direct buffered byte comparison replaced CRC-based content equality. A dedicated 1 MB benchmark measured:

| Comparison case | CRC implementation | Direct byte implementation | Result |
|---|---:|---:|---|
| Equal files | 0 ms average | 3 ms average | Direct was slower |
| Unequal files | 0 ms average | 4 ms average | Direct was slower |

The full suite and real-file equality regression pass, but the helper benchmark showed no benefit and the direct implementation was reverted. CRC behavior remains the implementation baseline.

### Lazy SyncQ invalidation experiment
Added dirty flags so sync/view filtered lists are rebuilt only after queue mutation or filter-mode changes. Focused add/delete/view tests now pass and the full suite remains green.

The existing sync-analyzer benchmark showed no meaningful queue-related speedup; its same queue counts were:

| Dataset | Baseline average | Queue-invalidated average | Delta |
|---|---:|---:|---:|
| 100 files | 77 ms | 95 ms | +18 ms (+23.4%) |
| 500 files | 77 ms | 100 ms | +23 ms (+29.9%) |
| 1500 files | 177 ms | 198 ms | +21 ms (+11.9%) |

The analyzer harness does not exercise the UI queue-filtering workflow. These results are treated as environmental/no-net-analyzer-impact evidence, while the experiment is retained for filtered-list correctness and mutation invalidation.

### Rejected early indexed-mirror traversal experiment
A first pair-aware mirror index was used to compare source and target entries after building one index for each side. The full suite passed, but the benchmark improvement was not repeatable or meaningful.

| Dataset | Baseline average | Indexed average | Delta |
|---|---:|---:|---:|
| 100 files | 77 ms | 82 ms | +5 ms (+6.5%) |
| 500 files | 77 ms | 75 ms | -2 ms (-2.6%) |
| 1500 files | 177 ms | 176 ms | -1 ms (-0.6%) |

The queue sizes remained unchanged at 134, 660, and 1952. The experiment was reverted because the first implementation produced only a small 500/1500 gain and added traversal/index complexity.

### Kept production traversal-index experiment
The production index for `ABMirror` and `BAMirror` builds one relative-path index per root, compares source/target entries, and processes only target-only entries for deletion. It preserves exclusion flags and analyzed-file/dir counters.

A controlled no-index run and final indexed run produced:

| Dataset | No index | With index | Delta |
|---|---:|---:|---:|
| 100 files | 103 ms | 86 ms | -17 ms (-16.5%) |
| 500 files | 94 ms | 81 ms | -13 ms (-13.8%) |
| 1500 files | 238 ms | 191 ms | -47 ms (-19.7%) |

Queue sizes remained unchanged at 134, 660, and 1952. Forward-mirror and reverse-mirror regressions pass, and the full Maven suite remains green. The implementation is kept for unidirectional mirror modes.

### Rejected comparison-predicate helper refactor
A helper-extraction refactor for modified/larger/larger-and-modified predicates passed all 19 tests but benchmarked at 178/218/365 ms for 100/500/1500 files, versus the existing no-refactor benchmark runs near 80/97/202 ms. The implementation was reverted because it added method overhead without reducing comparison work.

### Rejected bucketed queue views
A bucketed `SyncQ` implementation preserved add/delete/status-change behavior and passed all 19 tests. The existing ABMirror benchmark produced:

| Dataset | No buckets | With buckets | Delta |
|---|---:|---:|---:|
| 100 files | 90 ms | 69 ms | -21 ms (-23.3%) |
| 500 files | 87 ms | 81 ms | -6 ms (-6.9%) |
| 1500 files | 180 ms | 187 ms | +7 ms (+3.9%) |

Queue sizes remained unchanged. Because the large-tree result regressed and the analyzer harness does not exercise UI filtering workflows, the experiment is rejected and the implementation was reverted.

### Rejected bidirectional/custom traversal expansion
A staged implementation expanded indexed traversal beyond the kept unidirectional mirror modes. It produced 102/101/232 ms averages for 100/500/1500 files, compared with the existing 77/77/177 ms baseline. Queue sizes were unchanged, but the experiment was 32% slower at 100 files, 31% slower at 500 files, and 31% slower at 1500 files. The implementation was reverted and must not be restored without a controlled index/no-index comparison.

### Large-tree mirror strategy
The relative-path index is retained below 10,000 entries because it showed a controlled 100/500/1500-file benefit. At or above that threshold, `ABMirror` and `BAMirror` use streaming traversal to avoid retaining every relative path, path wrapper, and index entry in memory.

A local macOS filesystem comparison using 100,000 source files plus 100,000 target files produced:

| Tree state | Current indexed implementation | Version 1.53 streaming |
|---|---:|---:|
| Matching size/date | 8.51 s | 8.78 s |
| Modified source timestamps, three runs | 10.70 s average | 10.85 s average |

The result showed no repeatable large-tree benefit from the full index. The benchmark runner now includes 10,000, 50,000, and 100,000-file scenarios. For 10,000+ files it uses three iterations; the 100,000-file scenario uses one iteration by default to limit harness runtime.

### Threshold implementation benchmark results
The updated benchmark runner was executed on macOS with Java 17 using the threshold implementation:

| Dataset | Strategy | Average | Queue size |
|---|---|---:|---:|
| 10,000 files | Streaming | 1471 ms | 12,998 |
| 50,000 files | Streaming | 8463 ms | 64,976 |
| 100,000 files | Streaming | 18,492 ms | 129,948 |

### Conclusion
The first optimization pass in [src/main/java/job/Job.java](src/main/java/job/Job.java) was not a net win, but the second experiment was. The paired-path cache reduces repeated absolute-path remapping work, and the early return for identical file size/date avoids building a `SyncPair` for the common no-op case. The gains are most visible in the larger dataset, which is exactly where the hot path matters most.

The next optimization should still focus on deeper traversal reduction, since the current win is real but modest compared with the larger savings available from collapsing repeated full-tree passes.
