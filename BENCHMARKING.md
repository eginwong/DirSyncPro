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

### Conclusion
The first optimization pass in [src/main/java/job/Job.java](src/main/java/job/Job.java) was not a net win, but the second experiment was. The paired-path cache reduces repeated absolute-path remapping work, and the early return for identical file size/date avoids building a `SyncPair` for the common no-op case. The gains are most visible in the larger dataset, which is exactly where the hot path matters most.

The next optimization should still focus on deeper traversal reduction, since the current win is real but modest compared with the larger savings available from collapsing repeated full-tree passes.
